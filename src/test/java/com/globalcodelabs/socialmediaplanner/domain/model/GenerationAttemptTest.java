package com.globalcodelabs.socialmediaplanner.domain.model;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import org.junit.jupiter.api.Test;

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

    private static GenerationAttempt newAttempt(AiCapability capability) {
        return GenerationAttempt.start(
                UUID.randomUUID(), 1, capability, "openai",
                capability == AiCapability.IMAGE ? "image-test" : "gpt-test",
                PROMPT_HASH
        );
    }
}
