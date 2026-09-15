package com.globalcodelabs.socialmediaplanner.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishContentRequest;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PlatformPublishingFailureMapper;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishingProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.SocialPlatformClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.SocialPlatformClientFactory;
import com.globalcodelabs.socialmediaplanner.common.exception.ApiCredentialUnavailableException;
import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.CredentialType;
import com.globalcodelabs.socialmediaplanner.domain.enums.MediaType;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import com.globalcodelabs.socialmediaplanner.domain.model.PublishAttempt;
import com.globalcodelabs.socialmediaplanner.domain.repository.ContentRepository;
import com.globalcodelabs.socialmediaplanner.domain.repository.PublishAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PublishingServiceTest {

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private PublishAttemptRepository publishAttemptRepository;

    @Mock
    private ApiCredentialService apiCredentialService;

    @Mock
    private SocialPlatformClientFactory socialPlatformClientFactory;

    @Mock
    private SocialPlatformClient socialPlatformClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private GeneralSettingsService generalSettingsService;

    @Mock
    private SocialCredentialRefreshService credentialRefreshService;

    private PublishingService publishingService;

    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> invocation
                .<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0)
                .doInTransaction(null));
        lenient().doAnswer(invocation -> {
            invocation.<java.util.function.Consumer<org.springframework.transaction.TransactionStatus>>getArgument(0)
                    .accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        publishingService = new PublishingService(
                contentRepository,
                publishAttemptRepository,
                apiCredentialService,
                credentialRefreshService,
                socialPlatformClientFactory,
                transactionTemplate,
                generalSettingsService,
                new PublishingProperties(),
                new PlatformPublishingFailureMapper(new ObjectMapper())
        );
    }

    @Test
    void publishesNextDueContentAndRecordsSuccessfulAttempt() {
        Content content = scheduledLinkedInContent();
        ResolvedApiCredential credential = resolvedLinkedInCredential();
        when(contentRepository.lockNextDueContentId(any())).thenReturn(Optional.of(content.id()));
        when(contentRepository.findWithMediaById(content.id())).thenReturn(Optional.of(content));
        when(contentRepository.findById(content.id())).thenReturn(Optional.of(content));
        when(apiCredentialService.resolveActiveIncludingExpired(
                CredentialType.SOCIAL_PLATFORM, "linkedin"
        ))
                .thenReturn(credential);
        when(socialPlatformClientFactory.resolve(Platform.LINKEDIN))
                .thenReturn(socialPlatformClient);
        when(socialPlatformClient.publish(any()))
                .thenReturn("linkedin-post-123");
        when(socialPlatformClient.isPublished(any(), any())).thenReturn(true);

        boolean processed = publishingService.publishNextDueContent();

        assertThat(processed).isTrue();
        assertThat(content.status()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(content.publishedAt()).isNotNull();
        ArgumentCaptor<PublishContentRequest> requestCaptor =
                ArgumentCaptor.forClass(PublishContentRequest.class);
        verify(socialPlatformClient).publish(requestCaptor.capture());
        assertThat(requestCaptor.getValue().credential().accountIdentifier())
                .isEqualTo("urn:li:person:123");
        assertThat(requestCaptor.getValue().credential().accessToken())
                .isEqualTo("secret-access-token");
        assertThat(requestCaptor.getValue().media()).hasSize(1);

        ArgumentCaptor<PublishAttempt> attemptCaptor =
                ArgumentCaptor.forClass(PublishAttempt.class);
        verify(publishAttemptRepository).save(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().success()).isTrue();
        assertThat(attemptCaptor.getValue().externalPostId()).isEqualTo("linkedin-post-123");
        assertThat(attemptCaptor.getValue().errorMessage()).isNull();
    }

    @Test
    void keepsContentPublishingAndDoesNotRetryWhenProviderCallResultIsUncertain() {
        Content content = scheduledLinkedInContent();
        ResolvedApiCredential credential = resolvedLinkedInCredential();
        when(contentRepository.lockNextDueContentId(any())).thenReturn(Optional.of(content.id()));
        when(contentRepository.findWithMediaById(content.id())).thenReturn(Optional.of(content));
        when(contentRepository.findById(content.id())).thenReturn(Optional.of(content));
        when(apiCredentialService.resolveActiveIncludingExpired(
                CredentialType.SOCIAL_PLATFORM, "linkedin"
        ))
                .thenReturn(credential);
        when(socialPlatformClientFactory.resolve(Platform.LINKEDIN))
                .thenReturn(socialPlatformClient);
        when(socialPlatformClient.publish(any())).thenThrow(
                new IllegalStateException(
                        "Provider rejected token secret-access-token\nupstream unavailable"
                )
        );

        boolean processed = publishingService.publishNextDueContent();

        assertThat(processed).isTrue();
        assertThat(content.status()).isEqualTo(ContentStatus.PUBLISHING);
        assertThat(content.failureReason())
                .contains("[REDACTED]")
                .doesNotContain("secret-access-token")
                .doesNotContain("\n");
        verifyNoInteractions(publishAttemptRepository);
    }

    @Test
    void marksContentFailedWhenPlatformCredentialIsUnavailable() {
        Content content = scheduledLinkedInContent();
        when(contentRepository.lockNextDueContentId(any())).thenReturn(Optional.of(content.id()));
        when(contentRepository.findWithMediaById(content.id())).thenReturn(Optional.of(content));
        when(contentRepository.findById(content.id())).thenReturn(Optional.of(content));
        when(apiCredentialService.resolveActiveIncludingExpired(
                CredentialType.SOCIAL_PLATFORM, "linkedin"
        ))
                .thenThrow(new ApiCredentialUnavailableException(
                        CredentialType.SOCIAL_PLATFORM,
                        "linkedin"
                ));

        boolean processed = publishingService.publishNextDueContent();

        assertThat(processed).isTrue();
        assertThat(content.status()).isEqualTo(ContentStatus.FAILED);
        assertThat(content.failureReason()).contains("No active, unexpired credential");
        ArgumentCaptor<PublishAttempt> attemptCaptor =
                ArgumentCaptor.forClass(PublishAttempt.class);
        verify(publishAttemptRepository).save(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().success()).isFalse();
        assertThat(attemptCaptor.getValue().errorMessage()).isEqualTo(content.failureReason());
        verifyNoInteractions(socialPlatformClientFactory, socialPlatformClient);
    }

    @Test
    void returnsFalseWhenNoDueContentExists() {
        when(contentRepository.lockNextDueContentId(any())).thenReturn(Optional.empty());

        boolean processed = publishingService.publishNextDueContent();

        assertThat(processed).isFalse();
        verifyNoInteractions(
                publishAttemptRepository,
                apiCredentialService,
                socialPlatformClientFactory,
                socialPlatformClient
        );
    }

    private static Content scheduledLinkedInContent() {
        Content content = Content.create(
                "Scheduled LinkedIn post",
                Platform.LINKEDIN,
                ContentType.POST,
                "Scheduled post",
                List.of("spring", "java"),
                null
        );
        content.addMedia(
                MediaType.IMAGE,
                "generated/image/content.jpg",
                "https://cdn.example.com/content.jpg",
                "openai",
                "image-model"
        );
        content.schedule(OffsetDateTime.now().plusHours(1));
        return content;
    }

    private static ResolvedApiCredential resolvedLinkedInCredential() {
        return new ResolvedApiCredential(
                UUID.randomUUID(),
                CredentialType.SOCIAL_PLATFORM,
                "linkedin",
                "urn:li:person:123",
                "secret-access-token",
                null,
                OffsetDateTime.now().plusDays(30),
                null
        );
    }
}
