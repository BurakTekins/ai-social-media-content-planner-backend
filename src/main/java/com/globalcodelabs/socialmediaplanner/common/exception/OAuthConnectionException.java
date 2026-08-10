package com.globalcodelabs.socialmediaplanner.common.exception;

public class OAuthConnectionException extends RuntimeException {

    public static final String STATE_INVALID = "state_invalid";
    public static final String TOKEN_EXCHANGE_FAILED = "token_exchange_failed";
    public static final String ACCOUNT_LOOKUP_FAILED = "account_lookup_failed";
    public static final String PERMISSION_MISSING = "permission_missing";
    public static final String CONFIGURATION_ERROR = "configuration_error";

    private final String errorCode;
    private final String publicMessage;

    public OAuthConnectionException(String errorCode, String publicMessage) {
        super(publicMessage);
        this.errorCode = errorCode;
        this.publicMessage = publicMessage;
    }

    public OAuthConnectionException(String errorCode, String publicMessage, Throwable cause) {
        super(publicMessage, cause);
        this.errorCode = errorCode;
        this.publicMessage = publicMessage;
    }

    public String errorCode() {
        return errorCode;
    }

    public String publicMessage() {
        return publicMessage;
    }
}
