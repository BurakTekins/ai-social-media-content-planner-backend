package com.globalcodelabs.socialmediaplanner.common.exception;

public class AiProviderResponseException extends ApplicationException {

    private final String providerResponseId;
    private final String providerRequestId;

    public AiProviderResponseException(
            String message,
            String providerResponseId,
            String providerRequestId
    ) {
        super(ErrorCode.AI_PROVIDER_ERROR, message);
        this.providerResponseId = normalize(providerResponseId);
        this.providerRequestId = normalize(providerRequestId);
    }

    public AiProviderResponseException(
            String message,
            String providerResponseId,
            String providerRequestId,
            Throwable cause
    ) {
        super(ErrorCode.AI_PROVIDER_ERROR, message, cause);
        this.providerResponseId = normalize(providerResponseId);
        this.providerRequestId = normalize(providerRequestId);
    }

    public String providerResponseId() {
        return providerResponseId;
    }

    public String providerRequestId() {
        return providerRequestId;
    }

    @Override
    public String publicMessage() {
        return code().defaultMessage();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
