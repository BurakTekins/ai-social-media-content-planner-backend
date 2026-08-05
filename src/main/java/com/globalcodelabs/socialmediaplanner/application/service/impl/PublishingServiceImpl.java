package com.globalcodelabs.socialmediaplanner.application.service.impl;

import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.PlatformCredential;
import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.PublishContentRequest;
import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.PublishContentResult;
import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.PublishMedia;
import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.SocialPlatformClient;
import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.SocialPlatformClientResolver;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialResolver;
import com.globalcodelabs.socialmediaplanner.application.service.PublishingService;
import com.globalcodelabs.socialmediaplanner.application.service.ResolvedApiCredential;
import com.globalcodelabs.socialmediaplanner.common.exception.ContentNotFoundException;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.model.CredentialType;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;
import com.globalcodelabs.socialmediaplanner.domain.model.PublishAttempt;
import com.globalcodelabs.socialmediaplanner.domain.repository.ContentRepository;
import com.globalcodelabs.socialmediaplanner.domain.repository.PublishAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SocialPlatformClientResolver socialPlatformClientResolver;

    @Override
    @Transactional
    public boolean publishNextDueContent() {
        return contentRepository.lockNextDueContentId(OffsetDateTime.now())
                .map(this::publishLockedContent)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublishAttempt> findAttempts(UUID contentId) {
        if (!contentRepository.existsById(contentId)) {
            throw new ContentNotFoundException(contentId);
        }
        return publishAttemptRepository.findAllByContent_IdOrderByAttemptedAtDescIdDesc(contentId);
    }

    private boolean publishLockedContent(UUID contentId) {
        Content content = contentRepository.findWithMediaById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));
        String providerName = providerName(content.platform());
        MdcUtil.putProvider(providerName);

        try {
            return executePublish(content, providerName);
        } finally {
            MdcUtil.removeProvider();
        }
    }

    private boolean executePublish(Content content, String providerName) {
        ResolvedApiCredential resolvedCredential = null;
        PublishContentResult result;
        try {
            resolvedCredential = apiCredentialResolver.resolveActive(
                    CredentialType.SOCIAL_PLATFORM,
                    providerName
            );
            PublishContentRequest request = toPublishRequest(content, resolvedCredential);
            SocialPlatformClient client = socialPlatformClientResolver.resolve(content.platform());
            try {
                result = client.publish(request);
            } finally {
                MdcUtil.putProvider(providerName);
            }
        } catch (RuntimeException exception) {
            recordFailure(content, exception, resolvedCredential);
            return true;
        }

        content.markPublished();
        publishAttemptRepository.save(PublishAttempt.success(content, result.externalPostId()));
        log.info(
                "Content published contentId={} platform={} externalPostId={}",
                content.id(),
                content.platform(),
                result.externalPostId()
        );
        return true;
    }

    private void recordFailure(
            Content content,
            RuntimeException exception,
            ResolvedApiCredential resolvedCredential
    ) {
        String errorMessage = safeErrorMessage(exception, resolvedCredential);
        content.markFailed(errorMessage);
        publishAttemptRepository.save(PublishAttempt.failure(content, errorMessage));
        log.warn(
                "Content publishing failed contentId={} platform={} errorType={} error={}",
                content.id(),
                content.platform(),
                exception.getClass().getSimpleName(),
                errorMessage
        );
    }

    private static PublishContentRequest toPublishRequest(
            Content content,
            ResolvedApiCredential resolvedCredential
    ) {
        PlatformCredential credential = new PlatformCredential(
                resolvedCredential.providerName(),
                resolvedCredential.accountIdentifier(),
                resolvedCredential.accessToken()
        );
        return new PublishContentRequest(
                content.id(),
                content.platform(),
                content.contentType(),
                content.text(),
                content.hashtags(),
                content.media().stream()
                        .map(media -> new PublishMedia(
                                media.mediaType(),
                                media.storageKey(),
                                media.publicUrl(),
                                media.modelProvider()
                        ))
                        .toList(),
                credential
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
}
