package com.globalcodelabs.socialmediaplanner.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ApiCredentialNotFoundException extends RuntimeException {

    public ApiCredentialNotFoundException(UUID credentialId) {
        super("API credential not found: " + credentialId);
    }
}
