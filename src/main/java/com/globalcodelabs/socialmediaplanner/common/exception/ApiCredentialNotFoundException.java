package com.globalcodelabs.socialmediaplanner.common.exception;

import java.util.UUID;

public class ApiCredentialNotFoundException extends ApplicationException {

    public ApiCredentialNotFoundException(UUID credentialId) {
        super(ErrorCode.API_CREDENTIAL_NOT_FOUND, "API credential not found: " + credentialId);
    }
}
