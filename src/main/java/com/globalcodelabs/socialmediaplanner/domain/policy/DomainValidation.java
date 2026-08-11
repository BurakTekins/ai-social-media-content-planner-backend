package com.globalcodelabs.socialmediaplanner.domain.policy;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;

public final class DomainValidation {

    private DomainValidation() {
    }

    public static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainException(message);
        }
        return value.trim();
    }

    public static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
