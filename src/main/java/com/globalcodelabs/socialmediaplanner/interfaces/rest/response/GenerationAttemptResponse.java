package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationAttemptStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationAttempt;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GenerationAttemptResponse(
        UUID id,
        UUID batchId,
        int generationIndex,
        AiCapability capability,
        String provider,
        String model,
        GenerationAttemptStatus status,
        String statusMessage,
        int downloadRetryCount,
        OffsetDateTime providerSubmittedAt,
        OffsetDateTime artifactExpiresAt,
        OffsetDateTime nextDownloadRetryAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static GenerationAttemptResponse from(GenerationAttempt attempt) {
        return new GenerationAttemptResponse(
                attempt.id(),
                attempt.batchId(),
                attempt.generationIndex(),
                attempt.capability(),
                attempt.provider(),
                attempt.model(),
                attempt.status(),
                statusMessage(attempt.status()),
                attempt.downloadRetryCount(),
                attempt.providerSubmittedAt(),
                attempt.artifactExpiresAt(),
                attempt.nextDownloadRetryAt(),
                attempt.createdAt(),
                attempt.updatedAt()
        );
    }

    private static String statusMessage(GenerationAttemptStatus status) {
        return switch (status) {
            case DOWNLOAD_FAILED -> "Generated video is waiting for an automatic download retry";
            case AWAITING_REGENERATION_CONSENT ->
                    "Generated video is no longer available; a new paid generation requires approval";
            case REGENERATION_APPROVED -> "Video regeneration was approved";
            case FAILED -> "Generation attempt failed";
            case UNKNOWN -> "Generation result could not be confirmed";
            default -> null;
        };
    }
}
