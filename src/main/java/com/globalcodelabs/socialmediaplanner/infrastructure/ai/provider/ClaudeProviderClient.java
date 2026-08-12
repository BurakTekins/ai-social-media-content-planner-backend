package com.globalcodelabs.socialmediaplanner.infrastructure.ai.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialService;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiProvider;
import com.globalcodelabs.socialmediaplanner.common.exception.AiProviderResponseException;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiRestClientFactory;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiProviderProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class ClaudeProviderClient extends AbstractAiProviderClient {

    private static final String PROVIDER_NAME = AiProvider.ANTHROPIC.canonicalName();

    public ClaudeProviderClient(
            ApiCredentialService apiCredentialService,
            AiProviderProperties properties,
            AiRestClientFactory restClientFactory,
            AiProviderResponseDecoder responseDecoder
    ) {
        super(
                PROVIDER_NAME,
                "messages",
                "Anthropic request failed",
                apiCredentialService,
                properties,
                restClientFactory,
                responseDecoder
        );
    }

    @Override
    protected ProviderOutput execute(AiGenerationRequest request, String accessToken) {
        String baseUrl = properties.requireBaseUrl(PROVIDER_NAME);
        ResponseEntity<String> responseEntity = restClientFactory.forBaseUrl(baseUrl)
                .post()
                .uri(restClientFactory.endpoint(baseUrl, "v1/messages"))
                .header("x-api-key", accessToken)
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
        return new ProviderOutput(
                extractText(response, providerResponseId, providerRequestId),
                providerResponseId,
                providerRequestId
        );
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

    @Override
    protected void validateRequest(AiGenerationRequest request) {
        if (request.capability() != AiCapability.TEXT) {
            throw new UnsupportedOperationException("Anthropic does not generate " + request.capability());
        }
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
