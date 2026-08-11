package com.globalcodelabs.socialmediaplanner.common.exception;

import com.globalcodelabs.socialmediaplanner.domain.enums.CredentialType;

public class ApiCredentialAlreadyExistsException extends ApplicationException {

    public ApiCredentialAlreadyExistsException(CredentialType credentialType, String providerName) {
        super(
                ErrorCode.API_CREDENTIAL_ALREADY_EXISTS,
                "Credential already exists for " + credentialType + " provider " + providerName
        );
    }
}
