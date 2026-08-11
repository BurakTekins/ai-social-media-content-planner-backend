package com.globalcodelabs.socialmediaplanner.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Arrays;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    INVALID_ARGUMENT(HttpStatus.BAD_REQUEST, "Request argument is invalid"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Request body or parameters are invalid"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Requested resource was not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method is not allowed for this resource"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Request media type is not supported"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded payload is too large"),
    DOMAIN_RULE_VIOLATION(HttpStatus.BAD_REQUEST, "A business rule was violated"),
    INVALID_CONTENT_STATE_TRANSITION(HttpStatus.CONFLICT, "Content status transition is not allowed"),
    CONTENT_OPERATION_NOT_ALLOWED(HttpStatus.CONFLICT, "Content operation is not allowed"),
    CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Content was not found"),
    CONTENT_MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "Content media was not found"),
    GENERATION_BATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "Generation batch was not found"),
    GENERATION_BATCH_RETRY_CONFLICT(HttpStatus.CONFLICT, "Generation batch cannot be retried"),
    API_CREDENTIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "API credential was not found"),
    API_CREDENTIAL_ALREADY_EXISTS(HttpStatus.CONFLICT, "API credential already exists"),
    API_CREDENTIAL_UNAVAILABLE(HttpStatus.CONFLICT, "An active API credential is not available"),
    AI_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "AI provider request failed"),
    X_INTEGRATION_ERROR(HttpStatus.BAD_REQUEST, "X integration request failed"),
    OAUTH_STATE_INVALID("state_invalid", HttpStatus.BAD_REQUEST, "OAuth state is invalid or expired"),
    OAUTH_TOKEN_EXCHANGE_FAILED("token_exchange_failed", HttpStatus.BAD_GATEWAY, "OAuth token exchange failed"),
    OAUTH_ACCOUNT_LOOKUP_FAILED("account_lookup_failed", HttpStatus.BAD_GATEWAY, "OAuth account lookup failed"),
    OAUTH_PERMISSION_MISSING("permission_missing", HttpStatus.FORBIDDEN, "Required OAuth permission is missing"),
    OAUTH_CONFIGURATION_ERROR("configuration_error", HttpStatus.SERVICE_UNAVAILABLE, "OAuth integration is not configured"),
    OAUTH_CONNECTION_FAILED("oauth_connection_failed", HttpStatus.BAD_REQUEST, "OAuth connection failed"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final String code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.code = name();
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    ErrorCode(String code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public static ErrorCode fromCode(String code) {
        return Arrays.stream(values())
                .filter(errorCode -> errorCode.code.equals(code))
                .findFirst()
                .orElse(OAUTH_CONNECTION_FAILED);
    }
}
