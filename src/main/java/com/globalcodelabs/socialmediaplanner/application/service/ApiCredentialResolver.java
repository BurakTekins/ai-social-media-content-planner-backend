package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.domain.model.CredentialType;

public interface ApiCredentialResolver {

    ResolvedApiCredential resolveActive(CredentialType credentialType, String providerName);

    ResolvedApiCredential resolveActiveIncludingExpired(
            CredentialType credentialType,
            String providerName
    );
}
