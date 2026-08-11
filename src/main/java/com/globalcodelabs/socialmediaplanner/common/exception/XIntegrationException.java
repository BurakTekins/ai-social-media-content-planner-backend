package com.globalcodelabs.socialmediaplanner.common.exception;

public class XIntegrationException extends ApplicationException {

    public XIntegrationException(String message) {
        super(ErrorCode.X_INTEGRATION_ERROR, message);
    }

    public XIntegrationException(String message, Throwable cause) {
        super(ErrorCode.X_INTEGRATION_ERROR, message, cause);
    }
}
