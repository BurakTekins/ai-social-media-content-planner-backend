package com.globalcodelabs.socialmediaplanner.infrastructure.ai.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationResult;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialService;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiProvider;
import com.globalcodelabs.socialmediaplanner.common.exception.AiProviderResponseException;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiRestClientFactory;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiProviderProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.AiGeneratedVideoDownloader;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.RecoverableVideoProviderClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.VideoArtifactExpiredException;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.VideoModelRegistry;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.VideoProviderTaskFailedException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class GeminiProviderClient extends AbstractAiProviderClient implements RecoverableVideoProviderClient {

    private static final String PROVIDER_NAME = AiProvider.GEMINI.canonicalName();
    private static final Pattern MODEL_ID = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Set<String> VIDEO_DOWNLOAD_HOSTS = Set.of(
            "googleapis.com",
            "googleusercontent.com"
    );
    private static final Duration VIDEO_RETENTION = Duration.ofDays(2);

    private final VideoModelRegistry videoModelRegistry;
    private final AiGeneratedVideoDownloader videoDownloader;

    public GeminiProviderClient(
            ApiCredentialService apiCredentialService,
            AiProviderProperties properties,
            AiRestClientFactory restClientFactory,
            AiProviderResponseDecoder responseDecoder,
            VideoModelRegistry videoModelRegistry,
            AiGeneratedVideoDownloader videoDownloader
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
        this.videoModelRegistry = videoModelRegistry;
        this.videoDownloader = videoDownloader;
    }

    @Override
    protected ProviderOutput execute(AiGenerationRequest request, String accessToken) {
        return switch (request.capability()) {
            case TEXT -> generateText(request, accessToken);
            case IMAGE -> generateImage(request, accessToken);
            case VIDEO -> generateVideo(request, accessToken);
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

    private ProviderOutput generateVideo(AiGenerationRequest request, String accessToken) {
        videoModelRegistry.validate(request.provider(), request.model(), request.videoDurationSeconds());
        VideoTaskSubmission submission = submitVideo(request, accessToken);
        VideoArtifactReference artifact = awaitVideo(submission, accessToken);
        AiGenerationResult.GeneratedMedia media = downloadVideo(artifact, accessToken);
        return new ProviderOutput(
                "Gemini Veo operation completed",
                artifact.taskId(),
                artifact.providerRequestId(),
                media
        );
    }

    @Override
    public VideoTaskSubmission submitVideo(AiGenerationRequest request) {
        videoModelRegistry.validate(request.provider(), request.model(), request.videoDurationSeconds());
        return submitVideo(request, resolveAccessToken());
    }

    private VideoTaskSubmission submitVideo(AiGenerationRequest request, String accessToken) {
        String baseUrl = properties.requireBaseUrl(PROVIDER_NAME);
        ResponseEntity<String> startEntity = restClientFactory.forBaseUrl(baseUrl)
                .post()
                .uri(restClientFactory.endpoint(
                        baseUrl,
                        "models/" + request.model() + ":predictLongRunning"
                ))
                .header("x-goog-api-key", accessToken)
                .body(new VeoRequest(
                        List.of(new VeoInstance(request.prompt())),
                        new VeoParameters(request.videoDurationSeconds(), 1, "720p")
                ))
                .retrieve()
                .toEntity(String.class);
        AiProviderResponseDecoder.DecodedResponse<VeoOperation> start = responseDecoder.decode(
                startEntity,
                VeoOperation.class,
                "Gemini Veo",
                "x-goog-request-id",
                "x-request-id"
        );
        String operationName = requireText(
                start.body() == null ? null : start.body().name(),
                "Gemini Veo returned no operation name",
                null,
                start.providerRequestId()
        );
        return new VideoTaskSubmission(
                operationName,
                start.providerRequestId(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    @Override
    public VideoArtifactReference awaitVideo(VideoTaskSubmission submission) {
        return awaitVideo(submission, resolveAccessToken());
    }

    private VideoArtifactReference awaitVideo(
            VideoTaskSubmission submission,
            String accessToken
    ) {
        String baseUrl = properties.requireBaseUrl(PROVIDER_NAME);
        VeoOperation completed = pollVeoOperation(
                baseUrl,
                submission.taskId(),
                accessToken,
                properties.requireProvider(PROVIDER_NAME),
                submission.providerRequestId()
        );
        if (completed.error() != null) {
            throw new VideoProviderTaskFailedException(
                    "Gemini Veo task failed: " + safeProviderMessage(completed.error().message()),
                    submission.taskId(),
                    submission.providerRequestId()
            );
        }
        String providerRequestId = submission.providerRequestId();
        String videoUri = videoUri(completed, submission.taskId(), providerRequestId);
        return new VideoArtifactReference(
                submission.taskId(),
                providerRequestId,
                videoUri,
                OffsetDateTime.now(ZoneOffset.UTC).plus(VIDEO_RETENTION)
        );
    }

    @Override
    public VideoArtifactReference resolveCompletedVideo(
            String taskId,
            String providerRequestId,
            OffsetDateTime submittedAt
    ) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Gemini Veo operation name cannot be blank");
        }
        try {
            return awaitVideo(
                    new VideoTaskSubmission(taskId, providerRequestId, submittedAt),
                    resolveAccessToken()
            );
        } catch (org.springframework.web.client.RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404 || exception.getStatusCode().value() == 410) {
                throw new VideoArtifactExpiredException(
                        "Gemini Veo operation or generated video is no longer available",
                        exception
                );
            }
            throw exception;
        }
    }

    @Override
    public AiGenerationResult.GeneratedMedia downloadVideo(VideoArtifactReference artifact) {
        return downloadVideo(artifact, resolveAccessToken());
    }

    private AiGenerationResult.GeneratedMedia downloadVideo(
            VideoArtifactReference artifact,
            String accessToken
    ) {
        AiProviderProperties.Provider provider = properties.requireProvider(PROVIDER_NAME);
        AiGenerationResult.GeneratedMedia media;
        try {
            media = videoDownloader.download(
                    artifact.artifactUrl(),
                    Map.of("x-goog-api-key", accessToken),
                    VIDEO_DOWNLOAD_HOSTS,
                    provider
            );
        } catch (VideoArtifactExpiredException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiProviderResponseException(
                    "Gemini Veo video download failed",
                    artifact.taskId(),
                    artifact.providerRequestId(),
                    exception
            );
        }
        return media;
    }

    private VeoOperation pollVeoOperation(
            String baseUrl,
            String operationName,
            String accessToken,
            AiProviderProperties.Provider provider,
            String initialRequestId
    ) {
        long deadline = deadline(provider.getVideoPollTimeout());
        while (true) {
            waitForPoll(provider.getVideoPollInterval(), deadline, operationName, initialRequestId);
            ResponseEntity<String> pollEntity = restClientFactory.forBaseUrl(baseUrl)
                    .get()
                    .uri(restClientFactory.endpoint(baseUrl, operationName))
                    .header("x-goog-api-key", accessToken)
                    .retrieve()
                    .toEntity(String.class);
            AiProviderResponseDecoder.DecodedResponse<VeoOperation> poll = responseDecoder.decode(
                    pollEntity,
                    VeoOperation.class,
                    "Gemini Veo",
                    "x-goog-request-id",
                    "x-request-id"
            );
            VeoOperation operation = poll.body();
            if (operation == null) {
                throw new AiProviderResponseException(
                        "Gemini Veo returned an empty operation response",
                        operationName,
                        firstNonBlank(poll.providerRequestId(), initialRequestId)
                );
            }
            if (Boolean.TRUE.equals(operation.done())) {
                return operation;
            }
            if (System.nanoTime() >= deadline) {
                throw new AiProviderResponseException(
                        "Gemini Veo operation timed out",
                        operationName,
                        firstNonBlank(poll.providerRequestId(), initialRequestId)
                );
            }
        }
    }

    private static String videoUri(
            VeoOperation operation,
            String operationName,
            String providerRequestId
    ) {
        List<VeoGeneratedSample> samples = operation.response() == null
                || operation.response().generateVideoResponse() == null
                ? null
                : operation.response().generateVideoResponse().generatedSamples();
        String uri = samples == null || samples.isEmpty() || samples.getFirst() == null
                || samples.getFirst().video() == null
                ? null
                : samples.getFirst().video().uri();
        return requireText(
                uri,
                "Gemini Veo completed without a video URI",
                operationName,
                providerRequestId
        );
    }

    private static long deadline(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException("Gemini Veo poll timeout must be positive");
        }
        return System.nanoTime() + timeout.toNanos();
    }

    private static void waitForPoll(
            Duration interval,
            long deadline,
            String providerResponseId,
            String providerRequestId
    ) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalStateException("Gemini Veo poll interval must be positive");
        }
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new AiProviderResponseException(
                    "Gemini Veo operation timed out",
                    providerResponseId,
                    providerRequestId
            );
        }
        try {
            Thread.sleep(Duration.ofNanos(Math.min(interval.toNanos(), remainingNanos)));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderResponseException(
                    "Gemini Veo polling was interrupted",
                    providerResponseId,
                    providerRequestId,
                    exception
            );
        }
    }

    private static String requireText(
            String value,
            String message,
            String providerResponseId,
            String providerRequestId
    ) {
        if (value == null || value.isBlank()) {
            throw new AiProviderResponseException(message, providerResponseId, providerRequestId);
        }
        return value.trim();
    }

    private static String safeProviderMessage(String value) {
        if (value == null || value.isBlank()) {
            return "unknown provider error";
        }
        String normalized = value.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
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

    private record VeoRequest(List<VeoInstance> instances, VeoParameters parameters) {
    }

    private record VeoInstance(String prompt) {
    }

    private record VeoParameters(
            Integer durationSeconds,
            Integer numberOfVideos,
            String resolution
    ) {
    }

    private record VeoOperation(
            String name,
            Boolean done,
            VeoOperationResponse response,
            VeoOperationError error
    ) {
    }

    private record VeoOperationResponse(VeoGenerateVideoResponse generateVideoResponse) {
    }

    private record VeoGenerateVideoResponse(List<VeoGeneratedSample> generatedSamples) {
    }

    private record VeoGeneratedSample(VeoVideo video) {
    }

    private record VeoVideo(String uri) {
    }

    private record VeoOperationError(Integer code, String message, String status) {
    }

}
