package com.globalcodelabs.socialmediaplanner.common.exception;

import com.globalcodelabs.socialmediaplanner.domain.enums.CredentialType;

public class ApiCredentialUnavailableException extends ApplicationException {

    public ApiCredentialUnavailableException(CredentialType credentialType, String providerName) {
        super(
                ErrorCode.API_CREDENTIAL_UNAVAILABLE,
                "No active, unexpired credential is available for "
                        + credentialType + " provider " + providerName
        );
    }
}
