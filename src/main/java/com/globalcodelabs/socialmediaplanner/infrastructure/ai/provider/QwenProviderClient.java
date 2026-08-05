package com.globalcodelabs.socialmediaplanner.infrastructure.ai.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiGenerationResult;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiProviderClient;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialResolver;
import com.globalcodelabs.socialmediaplanner.application.service.ResolvedApiCredential;
import com.globalcodelabs.socialmediaplanner.common.exception.AiProviderResponseException;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import com.globalcodelabs.socialmediaplanner.domain.model.CredentialType;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiRestClientFactory;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.config.AiProviderProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class QwenProviderClient implements AiProviderClient {

    private static final String PROVIDER_NAME = "qwen";

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
                case VIDEO -> throw new UnsupportedOperationException(
                        "Qwen video generation requires Wan asynchronous task polling"
                );
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
            throw new IllegalStateException("Qwen request failed", exception);
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
                "Qwen",
                "x-request-id"
        );
        ChatResponse response = decoded.body();
        String providerResponseId = response == null ? null : normalizeId(response.id());
        String providerRequestId = firstNonBlank(
                response == null ? null : response.requestId(),
                decoded.providerRequestId()
        );
        ChatChoice choice = response == null || response.choices() == null || response.choices().isEmpty()
                ? null
                : response.choices().getFirst();
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || choice == null || choice.message() == null
                || choice.message().content() == null
                || choice.message().content().isBlank()) {
            throw new AiProviderResponseException(
                    "Qwen returned an empty text response",
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
        String mediaBaseUrl = properties.requireMediaBaseUrl(PROVIDER_NAME);
        ResponseEntity<String> responseEntity = restClientFactory.forBaseUrl(mediaBaseUrl)
                .post()
                .uri(restClientFactory.endpoint(
                        mediaBaseUrl,
                        "api/v1/services/aigc/multimodal-generation/generation"
                ))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .body(new QwenImageRequest(
                        request.model(),
                        new ImageInput(List.of(new ImageMessage(
                                "user",
                                List.of(new ImageContent(request.prompt()))
                        ))),
                        new ImageParameters(1, true, false)
                ))
                .retrieve()
                .toEntity(String.class);
        AiProviderResponseDecoder.DecodedResponse<QwenImageResponse> decoded = responseDecoder.decode(
                responseEntity,
                QwenImageResponse.class,
                "Qwen",
                "x-request-id"
        );
        QwenImageResponse response = decoded.body();
        String providerRequestId = firstNonBlank(
                response == null ? null : response.requestId(),
                decoded.providerRequestId()
        );
        ImageChoice choice = response == null || response.output() == null
                || response.output().choices() == null || response.output().choices().isEmpty()
                ? null
                : response.output().choices().getFirst();
        if (response == null || response.output() == null
                || response.output().choices() == null || response.output().choices().isEmpty()
                || choice == null || choice.message() == null
                || choice.message().content() == null
                || choice.message().content().isEmpty()) {
            throw new AiProviderResponseException(
                    "Qwen returned an empty image response",
                    null,
                    providerRequestId
            );
        }
        String imageUrl = choice.message().content().stream()
                .filter(Objects::nonNull)
                .map(ImageResponseContent::image)
                .filter(Objects::nonNull)
                .filter(image -> !image.isBlank())
                .findFirst()
                .orElseThrow(() -> new AiProviderResponseException(
                        "Qwen returned no image URL",
                        null,
                        providerRequestId
                ));
        try {
            if (!URI.create(imageUrl).isAbsolute()) {
                throw new AiProviderResponseException(
                        "Qwen returned an invalid image URL",
                        null,
                        providerRequestId
                );
            }
        } catch (IllegalArgumentException exception) {
            throw new AiProviderResponseException(
                    "Qwen returned an invalid image URL",
                    null,
                    providerRequestId,
                    exception
            );
        }
        return new ProviderOutput(
                imageUrl,
                null,
                providerRequestId
        );
    }

    private static void validateProvider(AiGenerationRequest request) {
        if (!PROVIDER_NAME.equals(request.provider())) {
            throw new IllegalArgumentException("QwenProviderClient cannot handle " + request.provider());
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
            @JsonProperty("response_format") ResponseFormat responseFormat
    ) {
    }

    private record ChatMessage(String role, String content) {
    }

    private record ResponseFormat(String type) {
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

    private record QwenImageRequest(
            String model,
            ImageInput input,
            ImageParameters parameters
    ) {
    }

    private record ImageInput(List<ImageMessage> messages) {
    }

    private record ImageMessage(String role, List<ImageContent> content) {
    }

    private record ImageContent(String text) {
    }

    private record ImageParameters(
            int n,
            @JsonProperty("prompt_extend") boolean promptExtend,
            boolean watermark
    ) {
    }

    private record QwenImageResponse(
            ImageOutput output,
            @JsonProperty("request_id") String requestId
    ) {
    }

    private record ImageOutput(List<ImageChoice> choices) {
    }

    private record ImageChoice(ImageResponseMessage message) {
    }

    private record ImageResponseMessage(List<ImageResponseContent> content) {
    }

    private record ImageResponseContent(String image) {
    }

    private record ProviderOutput(
            String output,
            String providerResponseId,
            String providerRequestId
    ) {
    }
}
