package com.globalcodelabs.socialmediaplanner.common.exception;

public class AiProviderResponseException extends RuntimeException {

    private final String providerResponseId;
    private final String providerRequestId;

    public AiProviderResponseException(
            String message,
            String providerResponseId,
            String providerRequestId
    ) {
        super(message);
        this.providerResponseId = normalize(providerResponseId);
        this.providerRequestId = normalize(providerRequestId);
    }

    public AiProviderResponseException(
            String message,
            String providerResponseId,
            String providerRequestId,
            Throwable cause
    ) {
        super(message, cause);
        this.providerResponseId = normalize(providerResponseId);
        this.providerRequestId = normalize(providerRequestId);
    }

    public String providerResponseId() {
        return providerResponseId;
    }

    public String providerRequestId() {
        return providerRequestId;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
