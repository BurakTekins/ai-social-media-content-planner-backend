package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PlatformCredential;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishContentRequest;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishMedia;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.SocialPlatformClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.SocialPlatformClientFactory;
import com.globalcodelabs.socialmediaplanner.common.exception.ContentNotFoundException;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.CredentialType;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import com.globalcodelabs.socialmediaplanner.domain.model.PublishAttempt;
import com.globalcodelabs.socialmediaplanner.domain.repository.ContentRepository;
import com.globalcodelabs.socialmediaplanner.domain.repository.PublishAttemptRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishingProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PlatformPublishingFailureMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublishingService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2_000;

    private final ContentRepository contentRepository;
    private final PublishAttemptRepository publishAttemptRepository;
    private final ApiCredentialService apiCredentialService;
    private final SocialCredentialRefreshService credentialRefreshService;
    private final SocialPlatformClientFactory socialPlatformClientFactory;
    private final TransactionTemplate transactionTemplate;
    private final GeneralSettingsService generalSettingsService;
    private final PublishingProperties publishingProperties;
    private final PlatformPublishingFailureMapper publishingFailureMapper;

    public boolean publishNextDueContent() {
        ClaimedPublication publication = claimNextDueContent();
        if (publication == null) {
            return false;
        }
        dispatch(publication);
        return true;
    }

    public boolean confirmNextPublishingContent() {
        PendingConfirmation pending = claimNextConfirmation();
        if (pending == null) {
            return false;
        }
        confirm(pending);
        return true;
    }

    public boolean reviewNextTimedOutPublishingContent() {
        GeneralSettings settings = generalSettingsService.get();
        OffsetDateTime deadline = OffsetDateTime.now(ZoneOffset.UTC)
                .minus(settings.publicationConfirmationTimeout());
        return Boolean.TRUE.equals(transactionTemplate.execute(transactionStatus -> contentRepository
                .lockNextTimedOutPublicationId(deadline)
                .map(contentId -> {
                    Content content = findContent(contentId);
                    content.requirePublicationReview(
                            "Platform publication could not be confirmed within "
                                    + settings.publicationConfirmationTimeout().toMinutes() + " minutes"
                    );
                    log.warn(
                            "Publication requires review contentId={} publishingStartedAt={}",
                            content.id(), content.publishingStartedAt()
                    );
                    return true;
                })
                .orElse(false)));
    }

    @Transactional(readOnly = true)
    public List<PublishAttempt> findAttempts(UUID contentId) {
        if (!contentRepository.existsById(contentId)) {
            throw new ContentNotFoundException(contentId);
        }
        List<PublishAttempt> attempts = publishAttemptRepository
                .findAllByContent_IdOrderByAttemptedAtDescIdDesc(contentId);
        attempts.forEach(PublishAttempt::platform);
        return attempts;
    }

    private ClaimedPublication claimNextDueContent() {
        return transactionTemplate.execute(transactionStatus -> contentRepository
                .lockNextDueContentId(OffsetDateTime.now(ZoneOffset.UTC))
                .map(contentId -> {
                    Content content = contentRepository.findWithMediaById(contentId)
                            .orElseThrow(() -> new ContentNotFoundException(contentId));
                    content.startPublishing(UUID.randomUUID());
                    log.info("Content publishing claimed contentId={} platform={}",
                            content.id(), content.platform());
                    return snapshot(content);
                })
                .orElse(null));
    }

    private PendingConfirmation claimNextConfirmation() {
        GeneralSettings settings = generalSettingsService.get();
        OffsetDateTime checkBefore = OffsetDateTime.now(ZoneOffset.UTC)
                .minus(settings.publicationConfirmationInterval());
        return transactionTemplate.execute(transactionStatus -> contentRepository
                .lockNextPendingConfirmationId(checkBefore)
                .map(contentId -> {
                    Content content = contentRepository.findById(contentId)
                            .orElseThrow(() -> new ContentNotFoundException(contentId));
                    content.recordPublicationCheck();
                    return new PendingConfirmation(
                            content.id(), content.platform(), content.externalPostId()
                    );
                })
                .orElse(null));
    }

    private void dispatch(ClaimedPublication publication) {
        String providerName = publication.platform().providerName();
        MdcUtil.putProvider(providerName);
        try {
            ResolvedApiCredential credential;
            SocialPlatformClient client;
            PublishContentRequest request;
            try {
                credential = resolveCredential(providerName);
                client = socialPlatformClientFactory.resolve(publication.platform());
                request = toPublishRequest(publication, credential);
            } catch (RuntimeException exception) {
                markDefinitiveFailure(
                        publication.contentId(), publication.platform(), exception, null
                );
                return;
            }

            String externalPostId;
            try {
                String returnedExternalPostId = client.publish(request);
                if (returnedExternalPostId == null || returnedExternalPostId.isBlank()) {
                    throw new IllegalStateException("Social platform did not return an external post id");
                }
                externalPostId = returnedExternalPostId.trim();
            } catch (RuntimeException exception) {
                PlatformPublishingFailureMapper.Failure failure = publishingFailureMapper.map(
                        publication.platform(), exception
                );
                if (failure.definitive()) {
                    markDefinitiveFailure(
                            publication.contentId(), publication.platform(), exception,
                            credential, failure
                    );
                } else {
                    recordUncertainty(
                            publication.contentId(), publication.platform(), exception,
                            credential, failure
                    );
                }
                return;
            }

            try {
                transactionTemplate.executeWithoutResult(transactionStatus -> {
                    Content content = findContent(publication.contentId());
                    content.recordExternalPostId(externalPostId);
                });
            } catch (RuntimeException exception) {
                log.error(
                        "Published content external id could not be persisted; automatic POST retry is disabled contentId={} errorType={}",
                        publication.contentId(), exception.getClass().getSimpleName(), exception
                );
                return;
            }
            confirm(new PendingConfirmation(
                    publication.contentId(), publication.platform(), externalPostId
            ));
        } finally {
            MdcUtil.removeProvider();
        }
    }

    private void confirm(PendingConfirmation pending) {
        String providerName = pending.platform().providerName();
        MdcUtil.putProvider(providerName);
        ResolvedApiCredential credential = null;
        try {
            credential = resolveCredential(providerName);
            SocialPlatformClient client = socialPlatformClientFactory.resolve(pending.platform());
            if (!client.isPublished(pending.externalPostId(), toPlatformCredential(credential))) {
                log.debug("Platform publication is not visible yet contentId={}", pending.contentId());
                return;
            }
            transactionTemplate.executeWithoutResult(transactionStatus -> {
                Content content = findContent(pending.contentId());
                if (content.status() != ContentStatus.PUBLISHING) {
                    return;
                }
                content.markPublished();
                publishAttemptRepository.save(PublishAttempt.success(content, pending.externalPostId()));
            });
            log.info(
                    "Content publication confirmed contentId={} platform={}",
                    pending.contentId(), pending.platform()
            );
        } catch (RuntimeException exception) {
            PlatformPublishingFailureMapper.Failure failure = publishingFailureMapper.map(
                    pending.platform(), exception
            );
            if (failure.definitive()) {
                markDefinitiveFailure(
                        pending.contentId(), pending.platform(), exception, credential, failure
                );
            } else {
                recordUncertainty(
                        pending.contentId(), pending.platform(), exception, credential, failure
                );
            }
        } finally {
            MdcUtil.removeProvider();
        }
    }

    private void markDefinitiveFailure(
            UUID contentId,
            Platform platform,
            RuntimeException exception,
            ResolvedApiCredential credential
    ) {
        markDefinitiveFailure(
                contentId, platform, exception, credential,
                publishingFailureMapper.map(platform, exception)
        );
    }

    private void markDefinitiveFailure(
            UUID contentId,
            Platform platform,
            RuntimeException exception,
            ResolvedApiCredential credential,
            PlatformPublishingFailureMapper.Failure failure
    ) {
        String errorMessage = safeErrorMessage(
                failure.technicalReason(platform, exception), credential
        );
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            Content content = findContent(contentId);
            content.markFailed(errorMessage);
            publishAttemptRepository.save(PublishAttempt.failure(content, errorMessage));
        });
        log.warn(
                "Content publishing definitively failed contentId={} platform={} httpStatus={} providerCode={} providerSubcode={} errorType={}",
                contentId, platform, failure.httpStatus(), failure.providerCode(),
                failure.providerSubcode(), exception.getClass().getSimpleName()
        );
    }

    private void recordUncertainty(
            UUID contentId,
            Platform platform,
            RuntimeException exception,
            ResolvedApiCredential credential
    ) {
        recordUncertainty(
                contentId, platform, exception, credential,
                publishingFailureMapper.map(platform, exception)
        );
    }

    private void recordUncertainty(
            UUID contentId,
            Platform platform,
            RuntimeException exception,
            ResolvedApiCredential credential,
            PlatformPublishingFailureMapper.Failure failure
    ) {
        String errorMessage = safeErrorMessage(
                failure.technicalReason(platform, exception), credential
        );
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            Content content = findContent(contentId);
            if (content.status() == ContentStatus.PUBLISHING) {
                content.markPublishingUncertain(errorMessage);
            }
        });
        log.warn(
                "Content publishing result is uncertain; automatic POST retry is disabled contentId={} platform={} httpStatus={} providerCode={} providerSubcode={} errorType={}",
                contentId, platform, failure.httpStatus(), failure.providerCode(),
                failure.providerSubcode(),
                exception.getClass().getSimpleName()
        );
    }

    private static PublishContentRequest toPublishRequest(
            ClaimedPublication publication,
            ResolvedApiCredential resolvedCredential
    ) {
        PlatformCredential credential = toPlatformCredential(resolvedCredential);
        return new PublishContentRequest(
                publication.contentId(), publication.platform(), publication.contentType(),
                publication.text(), publication.hashtags(), publication.media(),
                credential
        );
    }

    private static PlatformCredential toPlatformCredential(ResolvedApiCredential credential) {
        return new PlatformCredential(
                credential.providerName(), credential.accountIdentifier(), credential.accessToken()
        );
    }

    private Content findContent(UUID contentId) {
        return contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));
    }

    private ResolvedApiCredential resolveCredential(String providerName) {
        if (publishingProperties.mockModeEnabled()) {
            return apiCredentialService.resolveActiveIncludingExpired(
                    CredentialType.SOCIAL_PLATFORM,
                    providerName
            );
        }
        return credentialRefreshService.resolveValid(providerName);
    }

    private static ClaimedPublication snapshot(Content content) {
        return new ClaimedPublication(
                content.id(), content.platform(), content.contentType(), content.text(), content.hashtags(),
                content.media().stream().map(media -> new PublishMedia(
                        media.mediaType(), media.storageKey(), media.publicUrl(), media.modelProvider()
                )).toList()
        );
    }

    private static String safeErrorMessage(
            String technicalReason,
            ResolvedApiCredential resolvedCredential
    ) {
        String message = technicalReason;
        if (resolvedCredential != null) {
            message = redact(message, resolvedCredential.accessToken());
            message = redact(message, resolvedCredential.refreshToken());
        }
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (message.length() > MAX_ERROR_MESSAGE_LENGTH) {
            return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
        }
        return message;
    }

    private static String redact(String message, String sensitiveValue) {
        if (sensitiveValue == null || sensitiveValue.isEmpty()) {
            return message;
        }
        return message.replace(sensitiveValue, "[REDACTED]");
    }

    private record ClaimedPublication(
            UUID contentId,
            Platform platform,
            ContentType contentType,
            String text,
            List<String> hashtags,
            List<PublishMedia> media
    ) {
    }

    private record PendingConfirmation(UUID contentId, Platform platform, String externalPostId) {
    }
}
