package com.globalcodelabs.socialmediaplanner.infrastructure.ai.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialService;
import com.globalcodelabs.socialmediaplanner.common.exception.AiProviderResponseException;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiRestClientFactory;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.config.AiProviderProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OpenAiProviderClient extends AbstractAiProviderClient {

    private static final String PROVIDER_NAME = "openai";

    public OpenAiProviderClient(
            ApiCredentialService apiCredentialService,
            AiProviderProperties properties,
            AiRestClientFactory restClientFactory,
            AiProviderResponseDecoder responseDecoder
    ) {
        super(
                PROVIDER_NAME,
                "generate",
                "OpenAI request failed",
                apiCredentialService,
                properties,
                restClientFactory,
                responseDecoder
        );
    }

    @Override
    protected ProviderOutput execute(AiGenerationRequest request, String accessToken) {
        return switch (request.capability()) {
            case TEXT -> generateText(request, accessToken);
            case IMAGE -> generateImage(request, accessToken);
            case VIDEO -> throw unsupported(request.capability());
        };
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

}
