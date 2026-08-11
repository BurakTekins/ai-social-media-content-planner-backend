package com.globalcodelabs.socialmediaplanner.infrastructure.publishing;

public class DefinitivePublishingException extends RuntimeException {

    private final String providerCode;

    public DefinitivePublishingException(String providerCode) {
        super(providerCode);
        this.providerCode = providerCode;
    }

    public String providerCode() {
        return providerCode;
    }
}
