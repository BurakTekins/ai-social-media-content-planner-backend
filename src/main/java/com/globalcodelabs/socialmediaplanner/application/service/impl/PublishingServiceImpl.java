package com.globalcodelabs.socialmediaplanner.application.service.impl;

import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.PlatformCredential;
import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.PublishContentRequest;
import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.PublishContentResult;
import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.PublishMedia;
import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.SocialPlatformClient;
import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.SocialPlatformClientResolver;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialResolver;
import com.globalcodelabs.socialmediaplanner.application.service.GeneralSettings;
import com.globalcodelabs.socialmediaplanner.application.service.GeneralSettingsService;
import com.globalcodelabs.socialmediaplanner.application.service.PublishingService;
import com.globalcodelabs.socialmediaplanner.application.service.ResolvedApiCredential;
import com.globalcodelabs.socialmediaplanner.common.exception.ContentNotFoundException;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.CredentialType;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;
import com.globalcodelabs.socialmediaplanner.domain.model.PublishAttempt;
import com.globalcodelabs.socialmediaplanner.domain.repository.ContentRepository;
import com.globalcodelabs.socialmediaplanner.domain.repository.PublishAttemptRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublishingServiceImpl implements PublishingService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2_000;

    private final ContentRepository contentRepository;
    private final PublishAttemptRepository publishAttemptRepository;
    private final ApiCredentialResolver apiCredentialResolver;
    private final SocialCredentialRefreshService credentialRefreshService;
    private final SocialPlatformClientResolver socialPlatformClientResolver;
    private final TransactionTemplate transactionTemplate;
    private final GeneralSettingsService generalSettingsService;
    private final PublishingProperties publishingProperties;

    @Override
    public boolean publishNextDueContent() {
        ClaimedPublication publication = claimNextDueContent();
        if (publication == null) {
            return false;
        }
        dispatch(publication);
        return true;
    }

    @Override
    public boolean confirmNextPublishingContent() {
        PendingConfirmation pending = claimNextConfirmation();
        if (pending == null) {
            return false;
        }
        confirm(pending);
        return true;
    }

    @Override
    public boolean reviewNextTimedOutPublishingContent() {
        GeneralSettings settings = generalSettingsService.get();
        OffsetDateTime deadline = OffsetDateTime.now()
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
                            "Publication requires review contentId={} externalPostId={} publishingStartedAt={}",
                            content.id(), content.externalPostId(), content.publishingStartedAt()
                    );
                    return true;
                })
                .orElse(false)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublishAttempt> findAttempts(UUID contentId) {
        if (!contentRepository.existsById(contentId)) {
            throw new ContentNotFoundException(contentId);
        }
        return publishAttemptRepository.findAllByContent_IdOrderByAttemptedAtDescIdDesc(contentId);
    }

    private ClaimedPublication claimNextDueContent() {
        return transactionTemplate.execute(transactionStatus -> contentRepository
                .lockNextDueContentId(OffsetDateTime.now())
                .map(contentId -> {
                    Content content = contentRepository.findWithMediaById(contentId)
                            .orElseThrow(() -> new ContentNotFoundException(contentId));
                    content.startPublishing(UUID.randomUUID());
                    return snapshot(content);
                })
                .orElse(null));
    }

    private PendingConfirmation claimNextConfirmation() {
        GeneralSettings settings = generalSettingsService.get();
        OffsetDateTime checkBefore = OffsetDateTime.now()
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
        String providerName = providerName(publication.platform());
        MdcUtil.putProvider(providerName);
        try {
            ResolvedApiCredential credential;
            SocialPlatformClient client;
            PublishContentRequest request;
            try {
                credential = resolveCredential(providerName);
                client = socialPlatformClientResolver.resolve(publication.platform());
                request = toPublishRequest(publication, credential);
            } catch (RuntimeException exception) {
                markDefinitiveFailure(publication.contentId(), exception, null);
                return;
            }

            PublishContentResult result;
            try {
                result = client.publish(request);
            } catch (RuntimeException exception) {
                recordUncertainty(publication.contentId(), exception, credential);
                return;
            }

            try {
                transactionTemplate.executeWithoutResult(transactionStatus -> {
                    Content content = findContent(publication.contentId());
                    content.recordExternalPostId(result.externalPostId());
                });
            } catch (RuntimeException exception) {
                log.error(
                        "Published content external id could not be persisted; automatic POST retry is disabled contentId={}",
                        publication.contentId(), exception
                );
                return;
            }
            confirm(new PendingConfirmation(
                    publication.contentId(), publication.platform(), result.externalPostId()
            ));
        } finally {
            MdcUtil.removeProvider();
        }
    }

    private void confirm(PendingConfirmation pending) {
        String providerName = providerName(pending.platform());
        MdcUtil.putProvider(providerName);
        ResolvedApiCredential credential = null;
        try {
            credential = resolveCredential(providerName);
            SocialPlatformClient client = socialPlatformClientResolver.resolve(pending.platform());
            if (!client.isPublished(pending.externalPostId(), toPlatformCredential(credential))) {
                log.info(
                        "Platform publication is not visible yet contentId={} externalPostId={}",
                        pending.contentId(), pending.externalPostId()
                );
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
                    "Content publication confirmed contentId={} platform={} externalPostId={}",
                    pending.contentId(), pending.platform(), pending.externalPostId()
            );
        } catch (RuntimeException exception) {
            recordUncertainty(pending.contentId(), exception, credential);
        } finally {
            MdcUtil.removeProvider();
        }
    }

    private void markDefinitiveFailure(
            UUID contentId,
            RuntimeException exception,
            ResolvedApiCredential credential
    ) {
        String errorMessage = safeErrorMessage(exception, credential);
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            Content content = findContent(contentId);
            content.markFailed(errorMessage);
            publishAttemptRepository.save(PublishAttempt.failure(content, errorMessage));
        });
        log.warn("Content publishing failed before platform dispatch contentId={} error={}",
                contentId, errorMessage);
    }

    private void recordUncertainty(
            UUID contentId,
            RuntimeException exception,
            ResolvedApiCredential credential
    ) {
        String errorMessage = safeErrorMessage(exception, credential);
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            Content content = findContent(contentId);
            if (content.status() == ContentStatus.PUBLISHING) {
                content.markPublishingUncertain(errorMessage);
            }
        });
        log.warn(
                "Content publishing result is uncertain; automatic POST retry is disabled contentId={} errorType={} error={}",
                contentId,
                exception.getClass().getSimpleName(),
                errorMessage
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
            return apiCredentialResolver.resolveActiveIncludingExpired(
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

    private static String providerName(Platform platform) {
        return platform.name().toLowerCase(Locale.ROOT);
    }

    private static String safeErrorMessage(
            RuntimeException exception,
            ResolvedApiCredential resolvedCredential
    ) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
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
