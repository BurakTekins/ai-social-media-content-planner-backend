package com.globalcodelabs.socialmediaplanner.infrastructure.ai.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiGenerationResult;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiProviderClient;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialResolver;
import com.globalcodelabs.socialmediaplanner.application.service.ResolvedApiCredential;
import com.globalcodelabs.socialmediaplanner.common.exception.AiProviderResponseException;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import com.globalcodelabs.socialmediaplanner.domain.model.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.CredentialType;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiRestClientFactory;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.config.AiProviderProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeProviderClient implements AiProviderClient {

    private static final String PROVIDER_NAME = "anthropic";

    private final ApiCredentialResolver apiCredentialResolver;
    private final AiProviderProperties properties;
    private final AiRestClientFactory restClientFactory;
    private final AiProviderResponseDecoder responseDecoder;

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public AiGenerationResult generate(AiGenerationRequest request) {
        validateRequest(request);
        ResolvedApiCredential credential = apiCredentialResolver.resolveActive(
                CredentialType.AI_PROVIDER, PROVIDER_NAME
        );
        long startedAt = System.nanoTime();
        MdcUtil.putProvider(PROVIDER_NAME);
        try {
            log.info("AI provider call started operation=messages capability={} model={}",
                    request.capability(), request.model());
            String baseUrl = properties.requireBaseUrl(PROVIDER_NAME);
            ResponseEntity<String> responseEntity = restClientFactory.forBaseUrl(baseUrl)
                    .post()
                    .uri(restClientFactory.endpoint(baseUrl, "v1/messages"))
                    .header("x-api-key", credential.accessToken())
                    .header("anthropic-version", "2023-06-01")
                    .body(new ClaudeRequest(
                            request.model(),
                            4096,
                            List.of(new ClaudeMessage(
                                    "user",
                                    List.of(new ClaudeContentBlock("text", request.prompt()))
                            ))
                    ))
                    .retrieve()
                    .toEntity(String.class);
            AiProviderResponseDecoder.DecodedResponse<ClaudeResponse> decoded = responseDecoder.decode(
                    responseEntity,
                    ClaudeResponse.class,
                    "Anthropic",
                    "request-id"
            );
            ClaudeResponse response = decoded.body();
            String providerResponseId = response == null ? null : normalizeId(response.id());
            String providerRequestId = decoded.providerRequestId();
            String output = extractText(response, providerResponseId, providerRequestId);
            log.info("AI provider call completed operation=messages capability={} model={} durationMs={}",
                    request.capability(), request.model(), elapsedMilliseconds(startedAt));
            return new AiGenerationResult(
                    request.capability(),
                    PROVIDER_NAME,
                    request.model(),
                    providerResponseId,
                    providerRequestId,
                    output
            );
        } catch (RestClientException exception) {
            log.error("AI provider call failed operation=messages capability={} model={} durationMs={} errorType={} httpStatus={}",
                    request.capability(), request.model(), elapsedMilliseconds(startedAt),
                    AiProviderResponseDecoder.errorType(exception),
                    AiProviderResponseDecoder.httpStatus(exception));
            throw new IllegalStateException("Anthropic request failed", exception);
        } finally {
            MdcUtil.removeProvider();
        }
    }

    private static String extractText(
            ClaudeResponse response,
            String providerResponseId,
            String providerRequestId
    ) {
        if (response == null || response.content() == null) {
            throw new AiProviderResponseException(
                    "Anthropic returned an empty response",
                    providerResponseId,
                    providerRequestId
            );
        }
        String output = response.content().stream()
                .filter(Objects::nonNull)
                .filter(block -> "text".equals(block.type()))
                .map(ClaudeResponseBlock::text)
                .filter(Objects::nonNull)
                .reduce("", String::concat)
                .trim();
        if (output.isBlank()) {
            throw new AiProviderResponseException(
                    "Anthropic returned an empty text response",
                    providerResponseId,
                    providerRequestId
            );
        }
        return output;
    }

    private static void validateRequest(AiGenerationRequest request) {
        if (!PROVIDER_NAME.equals(request.provider())) {
            throw new IllegalArgumentException("ClaudeProviderClient cannot handle " + request.provider());
        }
        if (request.capability() != AiCapability.TEXT) {
            throw new UnsupportedOperationException("Anthropic does not generate " + request.capability());
        }
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static String normalizeId(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record ClaudeRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            List<ClaudeMessage> messages
    ) {
    }

    private record ClaudeMessage(String role, List<ClaudeContentBlock> content) {
    }

    private record ClaudeContentBlock(String type, String text) {
    }

    private record ClaudeResponse(String id, String model, List<ClaudeResponseBlock> content) {
    }

    private record ClaudeResponseBlock(String type, String text) {
    }
}
