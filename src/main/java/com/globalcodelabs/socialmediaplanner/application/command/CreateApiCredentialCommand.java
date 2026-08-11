package com.globalcodelabs.socialmediaplanner.application.command;

import com.globalcodelabs.socialmediaplanner.domain.enums.CredentialType;

import java.time.OffsetDateTime;

public record CreateApiCredentialCommand(
        CredentialType credentialType,
        String providerName,
        String accountIdentifier,
        String accessToken,
        String refreshToken,
        OffsetDateTime expiresAt
) {
}
