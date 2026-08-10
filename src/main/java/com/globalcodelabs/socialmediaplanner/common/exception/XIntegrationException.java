package com.globalcodelabs.socialmediaplanner.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class XIntegrationException extends RuntimeException {

    public XIntegrationException(String message) {
        super(message);
    }

    public XIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
