package com.globalcodelabs.socialmediaplanner.infrastructure.scheduler;

import com.globalcodelabs.socialmediaplanner.common.exception.ApiCredentialUnavailableException;
import com.globalcodelabs.socialmediaplanner.common.exception.AiProviderResponseException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

final class GenerationAttemptSupport {

    private GenerationAttemptSupport() {
    }

    static boolean submissionUnknown(RuntimeException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof AiProviderResponseException
                    || cause instanceof ApiCredentialUnavailableException
                    || cause instanceof IllegalArgumentException
                    || cause instanceof UnsupportedOperationException) {
                return false;
            }
            if (cause instanceof RestClientResponseException responseException) {
                return !responseException.getStatusCode().is4xxClientError();
            }
        }
        return true;
    }

    static String providerRequestId(RuntimeException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof AiProviderResponseException responseException
                    && responseException.providerRequestId() != null) {
                return responseException.providerRequestId();
            }
            if (cause instanceof RestClientResponseException responseException
                    && responseException.getResponseHeaders() != null) {
                for (String header : List.of("x-request-id", "request-id", "x-goog-request-id")) {
                    String value = responseException.getResponseHeaders().getFirst(header);
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    static String providerResponseId(RuntimeException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof AiProviderResponseException responseException
                    && responseException.providerResponseId() != null) {
                return responseException.providerResponseId();
            }
        }
        return null;
    }

    static String promptHash(String prompt) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(prompt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    static String errorMessage(Throwable exception) {
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        String sanitized = message
                .replaceAll("data:[^\\s,;]+;base64,[A-Za-z0-9+/=]+", "data:[REDACTED]")
                .replaceAll("(https?://[^\\s?]+)\\?[^\\s,]+", "$1?[REDACTED]")
                .replaceAll("(?i)(bearer\\s+)[^\\s,;]+", "$1[REDACTED]")
                .replaceAll(
                        "(?i)((?:api[-_ ]?key|token|authorization|x-api-key)\\s*[=:]\\s*)[^\\s,;]+",
                        "$1[REDACTED]"
                );
        return sanitized.length() <= 2000 ? sanitized : sanitized.substring(0, 2000);
    }
}
