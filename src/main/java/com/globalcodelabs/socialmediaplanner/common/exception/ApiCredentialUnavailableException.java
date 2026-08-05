package com.globalcodelabs.socialmediaplanner.common.exception;

import com.globalcodelabs.socialmediaplanner.domain.model.CredentialType;

public class ApiCredentialUnavailableException extends RuntimeException {

    public ApiCredentialUnavailableException(CredentialType credentialType, String providerName) {
        super("No active, unexpired credential is available for "
                + credentialType + " provider " + providerName);
    }
}
