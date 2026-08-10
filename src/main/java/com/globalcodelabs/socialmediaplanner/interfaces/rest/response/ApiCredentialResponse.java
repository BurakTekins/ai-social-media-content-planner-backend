package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.model.ApiCredential;
import com.globalcodelabs.socialmediaplanner.domain.model.CredentialType;
import com.globalcodelabs.socialmediaplanner.domain.model.CredentialValidationStatus;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record ApiCredentialResponse(
        UUID id,
        CredentialType credentialType,
        String providerName,
        String accountIdentifier,
        String accountDisplayName,
        Set<String> grantedScopes,
        boolean hasRefreshToken,
        OffsetDateTime expiresAt,
        OffsetDateTime refreshTokenExpiresAt,
        CredentialValidationStatus validationStatus,
        OffsetDateTime lastValidatedAt,
        String validationError,
        boolean expired,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ApiCredentialResponse from(ApiCredential credential) {
        return new ApiCredentialResponse(
                credential.id(),
                credential.credentialType(),
                credential.providerName(),
                credential.accountIdentifier(),
                credential.accountDisplayName(),
                credential.grantedScopes(),
                credential.hasRefreshToken(),
                credential.expiresAt(),
                credential.refreshTokenExpiresAt(),
                credential.validationStatus(),
                credential.lastValidatedAt(),
                credential.validationError(),
                credential.expiredAt(OffsetDateTime.now()),
                credential.active(),
                credential.createdAt(),
                credential.updatedAt()
        );
    }
}
