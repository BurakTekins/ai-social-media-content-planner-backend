package com.globalcodelabs.socialmediaplanner.infrastructure.publishing;

import java.util.Locale;

public record PlatformCredential(
        String providerName,
        String accountIdentifier,
        String accessToken
) {
    public PlatformCredential {
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("Platform provider name cannot be blank");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Platform access token cannot be blank");
        }
        providerName = providerName.trim().toLowerCase(Locale.ROOT);
        accountIdentifier = accountIdentifier == null ? null : accountIdentifier.trim();
        accessToken = accessToken.trim();
    }

    public String authorizationHeader() {
        return "Bearer " + accessToken;
    }
}
