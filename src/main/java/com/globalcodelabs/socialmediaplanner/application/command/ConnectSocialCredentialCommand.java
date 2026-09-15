package com.globalcodelabs.socialmediaplanner.application.command;

import java.time.OffsetDateTime;
import java.util.Set;

public record ConnectSocialCredentialCommand(
        String providerName,
        String accountIdentifier,
        String accountDisplayName,
        String accessToken,
        String refreshToken,
        OffsetDateTime expiresAt,
        OffsetDateTime refreshTokenExpiresAt,
        Set<String> grantedScopes
) {
}
