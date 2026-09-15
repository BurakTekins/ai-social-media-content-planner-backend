package com.globalcodelabs.socialmediaplanner.application.command;

import java.time.OffsetDateTime;

public record RefreshApiCredentialCommand(
        String accessToken,
        String refreshToken,
        OffsetDateTime expiresAt,
        OffsetDateTime refreshTokenExpiresAt
) {
}
