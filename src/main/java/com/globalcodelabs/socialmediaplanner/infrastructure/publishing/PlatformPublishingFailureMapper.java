package com.globalcodelabs.socialmediaplanner.infrastructure.publishing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class PlatformPublishingFailureMapper {

    private static final List<String> LINKEDIN_ERROR_CODES = List.of(
            "INVALID_URN_TYPE",
            "INVALID_URN_ID",
            "INVALID_IMAGE_ID",
            "INVALID_VIDEO_ID",
            "INVALID_CALL_TO_ACTION",
            "INVALID_URL",
            "MISSING_FIELD",
            "INVALID_VALUE_FOR_FIELD",
            "FIELD_LENGTH_TOO_LONG",
            "INVALID_VALUE_BLANK_FIELD",
            "EXPIRED_UPLOAD_URL",
            "MEDIA_ASSET_PROCESSING_FAILED",
            "MEDIA_ASSET_WAITING_UPLOAD",
            "UPDATING_ASSET_FAILED",
            "EMPTY_ACCESS_TOKEN",
            "ACCESS_DENIED",
            "NOT_FOUND",
            "CONFLICT",
            "UNPROCESSABLE_ENTITY",
            "TOO_MANY_REQUESTS",
            "INTERNAL_SERVER_ERROR",
            "SERVICE_UNAVAILABLE"
    );

    private final ObjectMapper objectMapper;

    public Failure map(Platform platform, RuntimeException exception) {
        DefinitivePublishingException definitiveException = findCause(
                exception, DefinitivePublishingException.class
        );
        if (definitiveException != null) {
            return new Failure(true, null, definitiveException.providerCode(), null);
        }

        RestClientResponseException responseException = findCause(
                exception, RestClientResponseException.class
        );
        if (responseException == null) {
            if (findCause(exception, IllegalArgumentException.class) != null) {
                return new Failure(true, null, "PLATFORM_REQUEST_INVALID", null);
            }
            return new Failure(false, null, null, null);
        }

        int httpStatus = responseException.getStatusCode().value();
        return switch (platform) {
            case LINKEDIN -> linkedinFailure(responseException, httpStatus);
            case INSTAGRAM -> instagramFailure(responseException, httpStatus);
            case TWITTER -> new Failure(
                    isDefinitiveHttpRejection(responseException), httpStatus, null, null
            );
        };
    }

    private Failure linkedinFailure(
            RestClientResponseException responseException,
            int httpStatus
    ) {
        JsonNode body = readBody(responseException);
        String providerCode = findLinkedInCode(body);
        Integer serviceErrorCode = integerValue(body, "serviceErrorCode");
        return new Failure(
                isDefinitiveHttpRejection(responseException),
                httpStatus,
                providerCode,
                serviceErrorCode
        );
    }

    private Failure instagramFailure(
            RestClientResponseException responseException,
            int httpStatus
    ) {
        JsonNode error = readBody(responseException).path("error");
        Integer providerCode = integerValue(error, "code");
        boolean temporaryProviderFailure = providerCode != null
                && (providerCode == -1 || providerCode == 1 || providerCode == 2);
        return new Failure(
                isDefinitiveHttpRejection(responseException) && !temporaryProviderFailure,
                httpStatus,
                providerCode == null ? null : providerCode.toString(),
                integerValue(error, "error_subcode")
        );
    }

    private JsonNode readBody(RestClientResponseException exception) {
        try {
            JsonNode body = objectMapper.readTree(exception.getResponseBodyAsByteArray());
            return body == null ? objectMapper.createObjectNode() : body;
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private static String findLinkedInCode(JsonNode body) {
        String message = body.path("message").asText("").toUpperCase(Locale.ROOT);
        String code = body.path("code").asText("").toUpperCase(Locale.ROOT);
        String documentedCode = LINKEDIN_ERROR_CODES.stream()
                .filter(candidate -> candidate.equals(code) || message.contains(candidate))
                .findFirst()
                .orElse(null);
        if (documentedCode != null) {
            return documentedCode;
        }
        if (message.contains("IMAGE ID") && message.contains("INVALID")) {
            return "INVALID_IMAGE_ID";
        }
        if (message.contains("VIDEO ID") && message.contains("INVALID")) {
            return "INVALID_VIDEO_ID";
        }
        if (message.contains("UPLOAD URL") && message.contains("EXPIRED")) {
            return "EXPIRED_UPLOAD_URL";
        }
        if (message.contains("MEDIA ASSET") && message.contains("FAILED PROCESSING")) {
            return "MEDIA_ASSET_PROCESSING_FAILED";
        }
        if (message.contains("MEDIA ASSET") && message.contains("WAITING UPLOAD")) {
            return "MEDIA_ASSET_WAITING_UPLOAD";
        }
        if (message.contains("COULD NOT FIND ENTITY")) {
            return "NOT_FOUND";
        }
        if (message.contains("FORBIDDEN") || message.contains("PERMISSION")) {
            return "ACCESS_DENIED";
        }
        if (message.contains("LENGTH EXCEEDS")) {
            return "FIELD_LENGTH_TOO_LONG";
        }
        return null;
    }

    private static Integer integerValue(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.canConvertToInt() ? value.intValue() : null;
    }

    private static boolean isDefinitiveHttpRejection(
            RestClientResponseException exception
    ) {
        return exception.getStatusCode().is4xxClientError()
                && exception.getStatusCode().value() != 408;
    }

    private static <T extends Throwable> T findCause(Throwable exception, Class<T> type) {
        Throwable current = exception;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    public record Failure(
            boolean definitive,
            Integer httpStatus,
            String providerCode,
            Integer providerSubcode
    ) {
        public String technicalReason(Platform platform, RuntimeException exception) {
            if (httpStatus == null && providerCode == null) {
                String message = exception.getMessage();
                return message == null || message.isBlank()
                        ? exception.getClass().getSimpleName()
                        : message;
            }
            StringBuilder reason = new StringBuilder("Platform request rejected provider=")
                    .append(platform.providerName());
            if (httpStatus != null) {
                reason.append(" HTTP ").append(httpStatus);
            }
            if (providerCode != null) {
                reason.append(" providerCode=").append(providerCode);
            }
            if (providerSubcode != null) {
                reason.append(" providerSubcode=").append(providerSubcode);
            }
            return reason.toString();
        }
    }
}
