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
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiProviderClient implements AiProviderClient {

    private static final String PROVIDER_NAME = "openai";

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
        validateProvider(request);
        ResolvedApiCredential credential = apiCredentialResolver.resolveActive(
                CredentialType.AI_PROVIDER, PROVIDER_NAME
        );
        long startedAt = System.nanoTime();
        MdcUtil.putProvider(PROVIDER_NAME);
        try {
            log.info("AI provider call started operation=generate capability={} model={}",
                    request.capability(), request.model());
            ProviderOutput generated = switch (request.capability()) {
                case TEXT -> generateText(request, credential.accessToken());
                case IMAGE -> generateImage(request, credential.accessToken());
                case VIDEO -> throw unsupported(request.capability());
            };
            log.info("AI provider call completed operation=generate capability={} model={} durationMs={}",
                    request.capability(), request.model(), elapsedMilliseconds(startedAt));
            return new AiGenerationResult(
                    request.capability(),
                    PROVIDER_NAME,
                    request.model(),
                    generated.providerResponseId(),
                    generated.providerRequestId(),
                    generated.output()
            );
        } catch (RestClientException exception) {
            log.error("AI provider call failed operation=generate capability={} model={} durationMs={} errorType={} httpStatus={}",
                    request.capability(), request.model(), elapsedMilliseconds(startedAt),
                    AiProviderResponseDecoder.errorType(exception),
                    AiProviderResponseDecoder.httpStatus(exception));
            throw new IllegalStateException("OpenAI request failed", exception);
        } finally {
            MdcUtil.removeProvider();
        }
    }

    private ProviderOutput generateText(AiGenerationRequest request, String accessToken) {
        String baseUrl = properties.requireBaseUrl(PROVIDER_NAME);
        ResponseEntity<String> responseEntity = restClientFactory.forBaseUrl(baseUrl)
                .post()
                .uri(restClientFactory.endpoint(baseUrl, "chat/completions"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .body(new ChatRequest(
                        request.model(),
                        List.of(new ChatMessage("user", request.prompt())),
                        false,
                        new ResponseFormat("json_object")
                ))
                .retrieve()
                .toEntity(String.class);
        AiProviderResponseDecoder.DecodedResponse<ChatResponse> decoded = responseDecoder.decode(
                responseEntity,
                ChatResponse.class,
                "OpenAI",
                "x-request-id"
        );
        ChatResponse response = decoded.body();
        String providerResponseId = response == null ? null : normalizeId(response.id());
        String providerRequestId = decoded.providerRequestId();
        ChatChoice choice = response == null || response.choices() == null || response.choices().isEmpty()
                ? null
                : response.choices().getFirst();
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || choice == null || choice.message() == null
                || choice.message().content() == null
                || choice.message().content().isBlank()) {
            throw new AiProviderResponseException(
                    "OpenAI returned an empty text response",
                    providerResponseId,
                    providerRequestId
            );
        }
        return new ProviderOutput(
                choice.message().content(),
                providerResponseId,
                providerRequestId
        );
    }

    private ProviderOutput generateImage(AiGenerationRequest request, String accessToken) {
        String baseUrl = properties.requireBaseUrl(PROVIDER_NAME);
        ResponseEntity<String> responseEntity = restClientFactory.forBaseUrl(baseUrl)
                .post()
                .uri(restClientFactory.endpoint(baseUrl, "images/generations"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .body(new ImageRequest(request.model(), request.prompt(), 1))
                .retrieve()
                .toEntity(String.class);
        AiProviderResponseDecoder.DecodedResponse<ImageResponse> decoded = responseDecoder.decode(
                responseEntity,
                ImageResponse.class,
                "OpenAI",
                "x-request-id"
        );
        ImageResponse response = decoded.body();
        String providerRequestId = decoded.providerRequestId();
        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new AiProviderResponseException(
                    "OpenAI returned an empty image response",
                    null,
                    providerRequestId
            );
        }
        ImageData image = response.data().getFirst();
        if (image == null) {
            throw new AiProviderResponseException(
                    "OpenAI returned an empty image response",
                    null,
                    providerRequestId
            );
        }
        if (image.url() != null && !image.url().isBlank()) {
            return new ProviderOutput(image.url(), null, providerRequestId);
        }
        if (image.base64Json() != null && !image.base64Json().isBlank()) {
            String format = response.outputFormat() == null ? "png" : response.outputFormat();
            return new ProviderOutput(
                    "data:image/" + format + ";base64," + image.base64Json(),
                    null,
                    providerRequestId
            );
        }
        throw new AiProviderResponseException(
                "OpenAI image response contains neither URL nor Base64 data",
                null,
                providerRequestId
        );
    }

    private static UnsupportedOperationException unsupported(AiCapability capability) {
        return new UnsupportedOperationException(
                "OpenAI " + capability + " generation requires an asynchronous media storage flow"
        );
    }

    private static void validateProvider(AiGenerationRequest request) {
        if (!PROVIDER_NAME.equals(request.provider())) {
            throw new IllegalArgumentException("OpenAiProviderClient cannot handle " + request.provider());
        }
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static String normalizeId(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record ChatRequest(
            String model,
            List<ChatMessage> messages,
            boolean stream,
            @JsonProperty("response_format") ResponseFormat responseFormat
    ) {
    }

    private record ResponseFormat(String type) {
    }

    private record ChatMessage(String role, String content) {
    }

    private record ChatResponse(String id, String model, List<ChatChoice> choices) {
    }

    private record ChatChoice(ChatMessage message) {
    }

    private record ImageRequest(String model, String prompt, int n) {
    }

    private record ImageResponse(
            List<ImageData> data,
            @JsonProperty("output_format") String outputFormat
    ) {
    }

    private record ImageData(
            String url,
            @JsonProperty("b64_json") String base64Json
    ) {
    }

    private record ProviderOutput(
            String output,
            String providerResponseId,
            String providerRequestId
    ) {
    }
}
