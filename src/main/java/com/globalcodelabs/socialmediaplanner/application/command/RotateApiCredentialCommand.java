package com.globalcodelabs.socialmediaplanner.application.command;

import java.time.OffsetDateTime;

public record RotateApiCredentialCommand(
        String accessToken,
        String refreshToken,
        OffsetDateTime expiresAt
) {
}
