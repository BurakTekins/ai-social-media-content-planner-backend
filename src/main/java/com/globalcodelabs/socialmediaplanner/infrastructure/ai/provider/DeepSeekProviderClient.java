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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekProviderClient implements AiProviderClient {

    private static final String PROVIDER_NAME = "deepseek";

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
            log.info("AI provider call started operation=chat-completions capability={} model={}",
                    request.capability(), request.model());
            String baseUrl = properties.requireBaseUrl(PROVIDER_NAME);
            ResponseEntity<String> responseEntity = restClientFactory.forBaseUrl(baseUrl)
                    .post()
                    .uri(restClientFactory.endpoint(baseUrl, "chat/completions"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential.accessToken())
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
            String output = extractText(response, providerResponseId, providerRequestId);
            log.info("AI provider call completed operation=chat-completions capability={} model={} durationMs={}",
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
            log.error("AI provider call failed operation=chat-completions capability={} model={} durationMs={} errorType={} httpStatus={}",
                    request.capability(), request.model(), elapsedMilliseconds(startedAt),
                    AiProviderResponseDecoder.errorType(exception),
                    AiProviderResponseDecoder.httpStatus(exception));
            throw new IllegalStateException("DeepSeek request failed", exception);
        } finally {
            MdcUtil.removeProvider();
        }
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

    private static void validateRequest(AiGenerationRequest request) {
        if (!PROVIDER_NAME.equals(request.provider())) {
            throw new IllegalArgumentException("DeepSeekProviderClient cannot handle " + request.provider());
        }
        if (request.capability() != AiCapability.TEXT) {
            throw new UnsupportedOperationException("DeepSeek does not generate " + request.capability());
        }
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String normalizeId(String value) {
        return value == null || value.isBlank() ? null : value;
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
