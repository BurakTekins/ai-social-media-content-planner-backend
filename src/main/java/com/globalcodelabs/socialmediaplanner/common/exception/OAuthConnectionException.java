package com.globalcodelabs.socialmediaplanner.common.exception;

public class OAuthConnectionException extends ApplicationException {

    private final String publicMessage;

    public OAuthConnectionException(ErrorCode errorCode, String publicMessage) {
        super(errorCode, publicMessage);
        this.publicMessage = publicMessage;
    }

    public OAuthConnectionException(ErrorCode errorCode, String publicMessage, Throwable cause) {
        super(errorCode, publicMessage, cause);
        this.publicMessage = publicMessage;
    }

    public String errorCode() {
        return code().code();
    }

    @Override
    public String publicMessage() {
        return publicMessage;
    }
}
