package com.globalcodelabs.socialmediaplanner.infrastructure.ai.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalcodelabs.socialmediaplanner.common.exception.AiProviderResponseException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class AiProviderResponseDecoder {

    private final ObjectMapper objectMapper;

    public <T> DecodedResponse<T> decode(
            ResponseEntity<String> responseEntity,
            Class<T> responseType,
            String providerDisplayName,
            String... requestIdHeaderNames
    ) {
        String providerRequestId = requestId(responseEntity, requestIdHeaderNames);
        String body = responseEntity.getBody();
        if (body == null || body.isBlank()) {
            return new DecodedResponse<>(null, providerRequestId);
        }
        try {
            return new DecodedResponse<>(objectMapper.readValue(body, responseType), providerRequestId);
        } catch (JsonProcessingException exception) {
            throw new AiProviderResponseException(
                    providerDisplayName + " returned an invalid JSON response",
                    null,
                    providerRequestId
            );
        }
    }

    public static String errorType(RestClientException exception) {
        return exception.getClass().getSimpleName();
    }

    public static String httpStatus(RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            return Integer.toString(responseException.getStatusCode().value());
        }
        return "none";
    }

    private static String requestId(
            ResponseEntity<String> responseEntity,
            String... requestIdHeaderNames
    ) {
        for (String headerName : requestIdHeaderNames) {
            String value = responseEntity.getHeaders().getFirst(headerName);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public record DecodedResponse<T>(T body, String providerRequestId) {
    }
}
