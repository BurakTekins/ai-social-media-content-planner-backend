package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.model.PublishAttempt;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PublishAttemptResponse(
        UUID id,
        UUID contentId,
        OffsetDateTime attemptedAt,
        boolean success,
        String errorCode,
        String errorMessage
) {
    public static PublishAttemptResponse from(PublishAttempt attempt, UUID contentId) {
        UserFacingError error = UserFacingError.publishing(
                attempt.platform(),
                null,
                attempt.errorMessage()
        );
        return new PublishAttemptResponse(
                attempt.id(),
                contentId,
                attempt.attemptedAt(),
                attempt.success(),
                error == null ? null : error.code(),
                error == null ? null : error.message()
        );
    }
}
