package com.globalcodelabs.socialmediaplanner.infrastructure.ai.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
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
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiProviderClient implements AiProviderClient {

    private static final String PROVIDER_NAME = "gemini";
    private static final Pattern MODEL_ID = Pattern.compile("[A-Za-z0-9._-]+");

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
            log.info("AI provider call started operation=generateContent capability={} model={}",
                    request.capability(), request.model());
            ProviderOutput generated = switch (request.capability()) {
                case TEXT -> generateText(request, credential.accessToken());
                case IMAGE -> generateImage(request, credential.accessToken());
                case VIDEO -> throw new UnsupportedOperationException(
                        "Gemini video generation requires Veo asynchronous operation polling and media download"
                );
            };
            log.info("AI provider call completed operation=generateContent capability={} model={} durationMs={}",
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
            log.error("AI provider call failed operation=generateContent capability={} model={} durationMs={} errorType={} httpStatus={}",
                    request.capability(), request.model(), elapsedMilliseconds(startedAt),
                    AiProviderResponseDecoder.errorType(exception),
                    AiProviderResponseDecoder.httpStatus(exception));
            throw new IllegalStateException("Gemini request failed", exception);
        } finally {
            MdcUtil.removeProvider();
        }
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

    private static void validateRequest(AiGenerationRequest request) {
        if (!PROVIDER_NAME.equals(request.provider())) {
            throw new IllegalArgumentException("GeminiProviderClient cannot handle " + request.provider());
        }
        if (!MODEL_ID.matcher(request.model()).matches()) {
            throw new IllegalArgumentException("Gemini model id contains invalid characters");
        }
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static String normalizeId(String value) {
        return value == null || value.isBlank() ? null : value;
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

    private record ProviderOutput(
            String output,
            String providerResponseId,
            String providerRequestId
    ) {
    }
}
