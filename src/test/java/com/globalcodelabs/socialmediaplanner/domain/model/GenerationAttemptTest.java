package com.globalcodelabs.socialmediaplanner.domain.model;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationAttemptStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationAttemptTest {

    private static final String PROMPT_HASH = "a".repeat(64);

    @Test
    void storesProviderIdsAndOutputBeforeContentMaterialization() {
        GenerationAttempt attempt = newAttempt(AiCapability.TEXT);

        attempt.succeed(
                AiCapability.TEXT,
                "openai",
                "gpt-test",
                "completion-123",
                "request-456",
                "{\"text\":\"ready\",\"hashtags\":[]}"
        );

        assertThat(attempt.status()).isEqualTo(GenerationAttemptStatus.SUCCEEDED);
        assertThat(attempt.providerResponseId()).isEqualTo("completion-123");
        assertThat(attempt.providerRequestId()).isEqualTo("request-456");
        assertThat(attempt.output()).contains("ready");
    }

    @Test
    void storedMediaReplacesInlinePayloadWithDurableStorageReference() {
        GenerationAttempt attempt = newAttempt(AiCapability.IMAGE);
        attempt.succeed(
                AiCapability.IMAGE,
                "openai",
                "image-test",
                null,
                "request-456",
                "data:image/png;base64,iVBORw0KGgo="
        );

        attempt.recordStoredMedia("media/image.png", "image/png");

        assertThat(attempt.storageKey()).isEqualTo("media/image.png");
        assertThat(attempt.mediaContentType()).isEqualTo("image/png");
        assertThat(attempt.output()).isNull();
    }

    @Test
    void recordsUnknownSubmissionWithoutPretendingItCanBeRecovered() {
        GenerationAttempt attempt = newAttempt(AiCapability.TEXT);

        attempt.fail("Connection timed out", true, "request-789");

        assertThat(attempt.status()).isEqualTo(GenerationAttemptStatus.UNKNOWN);
        assertThat(attempt.errorMessage()).isEqualTo("Connection timed out");
        assertThat(attempt.providerRequestId()).isEqualTo("request-789");
        assertThatThrownBy(() -> attempt.succeed(
                AiCapability.TEXT, "openai", "gpt-test", null, null, "output"
        )).isInstanceOf(DomainException.class);
    }

    @Test
    void recordsBothProviderIdsWhenReceivedResponseIsRejected() {
        GenerationAttempt attempt = newAttempt(AiCapability.TEXT);

        attempt.fail("Provider returned an empty response", false, "response-123", "request-456");

        assertThat(attempt.status()).isEqualTo(GenerationAttemptStatus.FAILED);
        assertThat(attempt.providerResponseId()).isEqualTo("response-123");
        assertThat(attempt.providerRequestId()).isEqualTo("request-456");
    }

    @Test
    void retriesOnlyDownloadAfterProviderVideoSucceeded() {
        GenerationAttempt attempt = newAttempt(AiCapability.VIDEO);
        OffsetDateTime submittedAt = OffsetDateTime.now();

        attempt.markSubmitted("task-123", "request-456", submittedAt);
        attempt.markProcessing();
        attempt.markProviderSucceeded(
                "task-123",
                "request-456",
                submittedAt.plusDays(2)
        );
        attempt.markDownloadFailed("Temporary storage error", submittedAt.plusMinutes(1));
        attempt.markProcessing();
        attempt.markProviderSucceeded(
                "task-123",
                "request-789",
                submittedAt.plusDays(2)
        );
        attempt.markDownloaded("media/video.mp4", "video/mp4");
        attempt.completeDownloadedVideo("Provider video task completed");

        assertThat(attempt.status()).isEqualTo(GenerationAttemptStatus.SUCCEEDED);
        assertThat(attempt.providerResponseId()).isEqualTo("task-123");
        assertThat(attempt.downloadRetryCount()).isEqualTo(1);
        assertThat(attempt.storageKey()).isEqualTo("media/video.mp4");
    }

    @Test
    void expiredArtifactRequiresExplicitRegenerationApproval() {
        GenerationAttempt attempt = newAttempt(AiCapability.VIDEO);
        OffsetDateTime submittedAt = OffsetDateTime.now();

        attempt.markSubmitted("task-123", null, submittedAt);
        attempt.markProcessing();
        attempt.markProviderSucceeded("task-123", null, submittedAt.plusDays(2));
        attempt.awaitRegenerationConsent("Artifact expired");

        assertThat(attempt.status())
                .isEqualTo(GenerationAttemptStatus.AWAITING_REGENERATION_CONSENT);

        attempt.approveRegeneration();

        assertThat(attempt.status()).isEqualTo(GenerationAttemptStatus.REGENERATION_APPROVED);
    }

    private static GenerationAttempt newAttempt(AiCapability capability) {
        return GenerationAttempt.start(
                UUID.randomUUID(), 1, capability, "openai",
                switch (capability) {
                    case IMAGE -> "image-test";
                    case VIDEO -> "video-test";
                    case TEXT -> "gpt-test";
                },
                PROMPT_HASH
        );
    }
}
