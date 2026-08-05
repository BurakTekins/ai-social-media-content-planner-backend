package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.model.PublishAttempt;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PublishAttemptResponse(
        UUID id,
        UUID contentId,
        OffsetDateTime attemptedAt,
        boolean success,
        String errorMessage,
        String externalPostId
) {
    public static PublishAttemptResponse from(PublishAttempt attempt, UUID contentId) {
        return new PublishAttemptResponse(
                attempt.id(),
                contentId,
                attempt.attemptedAt(),
                attempt.success(),
                attempt.errorMessage(),
                attempt.externalPostId()
        );
    }
}
