package com.globalcodelabs.socialmediaplanner.infrastructure.ai.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialService;
import com.globalcodelabs.socialmediaplanner.common.exception.AiProviderResponseException;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiRestClientFactory;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiProviderProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeepSeekProviderClient extends AbstractAiProviderClient {

    private static final String PROVIDER_NAME = "deepseek";

    public DeepSeekProviderClient(
            ApiCredentialService apiCredentialService,
            AiProviderProperties properties,
            AiRestClientFactory restClientFactory,
            AiProviderResponseDecoder responseDecoder
    ) {
        super(
                PROVIDER_NAME,
                "chat-completions",
                "DeepSeek request failed",
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
                .uri(restClientFactory.endpoint(baseUrl, "chat/completions"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .body(new ChatRequest(
                        request.model(),
                        List.of(new ChatMessage("user", request.prompt())),
                        false,
                        new ResponseFormat("json_object"),
                        new Thinking("disabled")
                ))
                .retrieve()
                .toEntity(String.class);
        AiProviderResponseDecoder.DecodedResponse<ChatResponse> decoded = responseDecoder.decode(
                responseEntity,
                ChatResponse.class,
                "DeepSeek",
                "x-request-id"
        );
        ChatResponse response = decoded.body();
        String providerResponseId = response == null ? null : normalizeId(response.id());
        String providerRequestId = firstNonBlank(
                response == null ? null : response.requestId(),
                decoded.providerRequestId()
        );
        return new ProviderOutput(
                extractText(response, providerResponseId, providerRequestId),
                providerResponseId,
                providerRequestId
        );
    }

    private static String extractText(
            ChatResponse response,
            String providerResponseId,
            String providerRequestId
    ) {
        ChatChoice choice = response == null || response.choices() == null || response.choices().isEmpty()
                ? null
                : response.choices().getFirst();
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || choice == null || choice.message() == null
                || choice.message().content() == null
                || choice.message().content().isBlank()) {
            throw new AiProviderResponseException(
                    "DeepSeek returned an empty text response",
                    providerResponseId,
                    providerRequestId
            );
        }
        return choice.message().content();
    }

    @Override
    protected void validateRequest(AiGenerationRequest request) {
        if (request.capability() != AiCapability.TEXT) {
            throw new UnsupportedOperationException("DeepSeek does not generate " + request.capability());
        }
    }

    private record ChatRequest(
            String model,
            List<ChatMessage> messages,
            boolean stream,
            @JsonProperty("response_format") ResponseFormat responseFormat,
            Thinking thinking
    ) {
    }

    private record ChatMessage(String role, String content) {
    }

    private record ResponseFormat(String type) {
    }

    private record Thinking(String type) {
    }

    private record ChatResponse(
            String id,
            String model,
            List<ChatChoice> choices,
            @JsonProperty("request_id") String requestId
    ) {
    }

    private record ChatChoice(ChatMessage message) {
    }
}
