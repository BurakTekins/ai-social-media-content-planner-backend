package com.globalcodelabs.socialmediaplanner.infrastructure.ai.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialService;
import com.globalcodelabs.socialmediaplanner.common.exception.AiProviderResponseException;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiRestClientFactory;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.config.AiProviderProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class GeminiProviderClient extends AbstractAiProviderClient {

    private static final String PROVIDER_NAME = "gemini";
    private static final Pattern MODEL_ID = Pattern.compile("[A-Za-z0-9._-]+");

    public GeminiProviderClient(
            ApiCredentialService apiCredentialService,
            AiProviderProperties properties,
            AiRestClientFactory restClientFactory,
            AiProviderResponseDecoder responseDecoder
    ) {
        super(
                PROVIDER_NAME,
                "generateContent",
                "Gemini request failed",
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
            case VIDEO -> throw new UnsupportedOperationException(
                    "Gemini video generation requires Veo asynchronous operation polling and media download"
            );
        };
    }

    private ProviderOutput generateText(AiGenerationRequest request, String accessToken) {
        ResponseEntity<String> responseEntity = call(
                request,
                accessToken,
                new GenerationConfig("application/json", null)
        );
        AiProviderResponseDecoder.DecodedResponse<GeminiResponse> decoded = responseDecoder.decode(
                responseEntity,
                GeminiResponse.class,
                "Gemini",
                "x-goog-request-id",
                "x-request-id"
        );
        GeminiResponse response = decoded.body();
        String providerResponseId = response == null ? null : normalizeId(response.responseId());
        String providerRequestId = decoded.providerRequestId();
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw emptyResponse(response, providerResponseId, providerRequestId);
        }
        List<GeminiResponsePart> parts = responseParts(response, providerResponseId, providerRequestId);
        String output = parts.stream()
                .filter(part -> !Boolean.TRUE.equals(part.thought()))
                .map(GeminiResponsePart::text)
                .filter(Objects::nonNull)
                .reduce("", String::concat)
                .trim();
        if (output.isBlank()) {
            throw new AiProviderResponseException(
                    "Gemini returned an empty text response",
                    providerResponseId,
                    providerRequestId
            );
        }
        return new ProviderOutput(
                output,
                providerResponseId,
                providerRequestId
        );
    }

    private ProviderOutput generateImage(AiGenerationRequest request, String accessToken) {
        ResponseEntity<String> responseEntity = call(
                request,
                accessToken,
                new GenerationConfig(null, List.of("IMAGE"))
        );
        AiProviderResponseDecoder.DecodedResponse<GeminiResponse> decoded = responseDecoder.decode(
                responseEntity,
                GeminiResponse.class,
                "Gemini",
                "x-goog-request-id",
                "x-request-id"
        );
        GeminiResponse response = decoded.body();
        String providerResponseId = response == null ? null : normalizeId(response.responseId());
        String providerRequestId = decoded.providerRequestId();
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw emptyResponse(response, providerResponseId, providerRequestId);
        }
        InlineData image = responseParts(response, providerResponseId, providerRequestId).stream()
                .map(GeminiResponsePart::inlineData)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new AiProviderResponseException(
                        "Gemini returned no image data",
                        providerResponseId,
                        providerRequestId
                ));
        if (image.mimeType() == null || image.mimeType().isBlank()
                || image.data() == null || image.data().isBlank()) {
            throw new AiProviderResponseException(
                    "Gemini returned incomplete image data",
                    providerResponseId,
                    providerRequestId
            );
        }
        return new ProviderOutput(
                "data:" + image.mimeType() + ";base64," + image.data(),
                providerResponseId,
                providerRequestId
        );
    }

    private static List<GeminiResponsePart> responseParts(
            GeminiResponse response,
            String providerResponseId,
            String providerRequestId
    ) {
        GeminiCandidate candidate = response.candidates().getFirst();
        GeminiResponseContent content = candidate == null ? null : candidate.content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            throw new AiProviderResponseException(
                    "Gemini returned an empty candidate",
                    providerResponseId,
                    providerRequestId
            );
        }
        return content.parts();
    }

    private ResponseEntity<String> call(
            AiGenerationRequest request,
            String accessToken,
            GenerationConfig generationConfig
    ) {
        String baseUrl = properties.requireBaseUrl(PROVIDER_NAME);
        return restClientFactory.forBaseUrl(baseUrl)
                .post()
                .uri(restClientFactory.endpoint(
                        baseUrl,
                        "models/" + request.model() + ":generateContent"
                ))
                .header("x-goog-api-key", accessToken)
                .body(new GeminiRequest(
                        List.of(new GeminiContent(
                                "user",
                                List.of(new GeminiRequestPart(request.prompt()))
                        )),
                        generationConfig
                ))
                .retrieve()
                .toEntity(String.class);
    }

    private static AiProviderResponseException emptyResponse(
            GeminiResponse response,
            String providerResponseId,
            String providerRequestId
    ) {
        String blockReason = response == null || response.promptFeedback() == null
                ? null
                : response.promptFeedback().blockReason();
        return new AiProviderResponseException(
                blockReason == null
                        ? "Gemini returned no candidates"
                        : "Gemini blocked the prompt: " + blockReason,
                providerResponseId,
                providerRequestId
        );
    }

    @Override
    protected void validateRequest(AiGenerationRequest request) {
        if (!MODEL_ID.matcher(request.model()).matches()) {
            throw new IllegalArgumentException("Gemini model id contains invalid characters");
        }
    }

    private record GeminiRequest(
            List<GeminiContent> contents,
            GenerationConfig generationConfig
    ) {
    }

    private record GeminiContent(String role, List<GeminiRequestPart> parts) {
    }

    private record GeminiRequestPart(String text) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GenerationConfig(
            String responseMimeType,
            List<String> responseModalities
    ) {
    }

    private record GeminiResponse(
            List<GeminiCandidate> candidates,
            PromptFeedback promptFeedback,
            String responseId
    ) {
    }

    private record GeminiCandidate(GeminiResponseContent content, String finishReason) {
    }

    private record GeminiResponseContent(List<GeminiResponsePart> parts) {
    }

    private record GeminiResponsePart(
            String text,
            InlineData inlineData,
            Boolean thought
    ) {
    }

    private record InlineData(String mimeType, String data) {
    }

    private record PromptFeedback(String blockReason) {
    }

}
