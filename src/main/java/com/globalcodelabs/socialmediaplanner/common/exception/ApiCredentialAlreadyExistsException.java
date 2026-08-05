package com.globalcodelabs.socialmediaplanner.common.exception;

import com.globalcodelabs.socialmediaplanner.domain.model.CredentialType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ApiCredentialAlreadyExistsException extends RuntimeException {

    public ApiCredentialAlreadyExistsException(CredentialType credentialType, String providerName) {
        super("Credential already exists for " + credentialType + " provider " + providerName);
    }
}
