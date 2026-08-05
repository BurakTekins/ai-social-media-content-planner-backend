package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.model.ApiCredential;
import com.globalcodelabs.socialmediaplanner.domain.model.CredentialType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApiCredentialResponse(
        UUID id,
        CredentialType credentialType,
        String providerName,
        String accountIdentifier,
        boolean hasRefreshToken,
        OffsetDateTime expiresAt,
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
                credential.hasRefreshToken(),
                credential.expiresAt(),
                credential.expiredAt(OffsetDateTime.now()),
                credential.active(),
                credential.createdAt(),
                credential.updatedAt()
        );
    }
}
