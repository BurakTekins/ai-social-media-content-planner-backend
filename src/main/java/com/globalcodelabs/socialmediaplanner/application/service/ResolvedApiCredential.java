package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.domain.model.CredentialType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ResolvedApiCredential(
        UUID credentialId,
        CredentialType credentialType,
        String providerName,
        String accountIdentifier,
        String accessToken,
        String refreshToken,
        OffsetDateTime expiresAt
) {
}
