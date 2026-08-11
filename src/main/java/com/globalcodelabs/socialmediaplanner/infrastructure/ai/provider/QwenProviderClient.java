package com.globalcodelabs.socialmediaplanner.infrastructure.ai.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationResult;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialService;
import com.globalcodelabs.socialmediaplanner.common.exception.AiProviderResponseException;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiRestClientFactory;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiProviderProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.AiGeneratedVideoDownloader;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.RecoverableVideoProviderClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.VideoArtifactExpiredException;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.VideoModelRegistry;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.VideoProviderTaskFailedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class QwenProviderClient extends AbstractAiProviderClient implements RecoverableVideoProviderClient {

    private static final String PROVIDER_NAME = "qwen";
    private static final Set<String> VIDEO_DOWNLOAD_HOSTS = Set.of("aliyuncs.com");
    private static final Duration TASK_RETENTION = Duration.ofHours(24);

    private final VideoModelRegistry videoModelRegistry;
    private final AiGeneratedVideoDownloader videoDownloader;

    public QwenProviderClient(
            ApiCredentialService apiCredentialService,
            AiProviderProperties properties,
            AiRestClientFactory restClientFactory,
            AiProviderResponseDecoder responseDecoder,
            VideoModelRegistry videoModelRegistry,
            AiGeneratedVideoDownloader videoDownloader
    ) {
        super(
                PROVIDER_NAME,
                "generate",
                "Qwen request failed",
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

    private ProviderOutput generateVideo(AiGenerationRequest request, String accessToken) {
        videoModelRegistry.validate(request.provider(), request.model(), request.videoDurationSeconds());
        VideoTaskSubmission submission = submitVideo(request, accessToken);
        VideoArtifactReference artifact = awaitVideo(submission, accessToken, false);
        AiGenerationResult.GeneratedMedia media = downloadVideo(artifact);
        return new ProviderOutput(
                "Qwen Wan task completed",
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
        String baseUrl = properties.requireVideoBaseUrl(PROVIDER_NAME);
        WanVideoParameters parameters = request.model().toLowerCase(Locale.ROOT).startsWith("wan2.7")
                ? new WanVideoParameters(null, "720P", "16:9", request.videoDurationSeconds(), true, false)
                : new WanVideoParameters("1280*720", null, null, request.videoDurationSeconds(), true, false);
        ResponseEntity<String> startEntity = restClientFactory.forBaseUrl(baseUrl)
                .post()
                .uri(restClientFactory.endpoint(
                        baseUrl,
                        "services/aigc/video-generation/video-synthesis"
                ))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-DashScope-Async", "enable")
                .body(new WanVideoRequest(
                        request.model(),
                        new WanVideoInput(request.prompt()),
                        parameters
                ))
                .retrieve()
                .toEntity(String.class);
        AiProviderResponseDecoder.DecodedResponse<WanTaskResponse> start = responseDecoder.decode(
                startEntity,
                WanTaskResponse.class,
                "Qwen Wan",
                "x-request-id"
        );
        String requestId = firstNonBlank(
                start.body() == null ? null : start.body().requestId(),
                start.providerRequestId()
        );
        WanTaskResponse startBody = start.body();
        String taskId = requireText(
                startBody == null || startBody.output() == null
                        ? null
                        : startBody.output().taskId(),
                "Qwen Wan returned no task ID: " + safeProviderMessage(
                        startBody == null ? null : startBody.message()
                ),
                null,
                requestId
        );
        return new VideoTaskSubmission(taskId, requestId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Override
    public VideoArtifactReference awaitVideo(VideoTaskSubmission submission) {
        return awaitVideo(submission, resolveAccessToken(), false);
    }

    private VideoArtifactReference awaitVideo(
            VideoTaskSubmission submission,
            String accessToken,
            boolean expiredOnUnknown
    ) {
        String baseUrl = properties.requireVideoBaseUrl(PROVIDER_NAME);
        WanTaskResponse completed = pollWanTask(
                baseUrl,
                submission.taskId(),
                accessToken,
                properties.requireProvider(PROVIDER_NAME),
                submission.providerRequestId(),
                expiredOnUnknown
        );
        WanTaskOutput output = completed.output();
        String videoUrl = requireText(
                output == null ? null : output.videoUrl(),
                "Qwen Wan completed without a video URL",
                submission.taskId(),
                firstNonBlank(completed.requestId(), submission.providerRequestId())
        );
        return new VideoArtifactReference(
                submission.taskId(),
                firstNonBlank(completed.requestId(), submission.providerRequestId()),
                videoUrl,
                submission.submittedAt().plus(TASK_RETENTION)
        );
    }

    @Override
    public VideoArtifactReference resolveCompletedVideo(
            String taskId,
            String providerRequestId,
            OffsetDateTime submittedAt
    ) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Qwen Wan task ID cannot be blank");
        }
        return awaitVideo(
                new VideoTaskSubmission(
                        taskId,
                        providerRequestId,
                        submittedAt
                ),
                resolveAccessToken(),
                true
        );
    }

    @Override
    public AiGenerationResult.GeneratedMedia downloadVideo(VideoArtifactReference artifact) {
        AiProviderProperties.Provider provider = properties.requireProvider(PROVIDER_NAME);
        AiGenerationResult.GeneratedMedia media;
        try {
            media = videoDownloader.download(
                    artifact.artifactUrl(),
                    Map.of(),
                    VIDEO_DOWNLOAD_HOSTS,
                    provider
            );
        } catch (VideoArtifactExpiredException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiProviderResponseException(
                    "Qwen Wan video download failed",
                    artifact.taskId(),
                    artifact.providerRequestId(),
                    exception
            );
        }
        return media;
    }

    private WanTaskResponse pollWanTask(
            String baseUrl,
            String taskId,
            String accessToken,
            AiProviderProperties.Provider provider,
            String initialRequestId,
            boolean expiredOnUnknown
    ) {
        long deadline = deadline(provider.getVideoPollTimeout());
        while (true) {
            waitForPoll(provider.getVideoPollInterval(), deadline, taskId, initialRequestId);
            ResponseEntity<String> pollEntity = restClientFactory.forBaseUrl(baseUrl)
                    .get()
                    .uri(restClientFactory.endpoint(baseUrl, "tasks/" + taskId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .toEntity(String.class);
            AiProviderResponseDecoder.DecodedResponse<WanTaskResponse> poll = responseDecoder.decode(
                    pollEntity,
                    WanTaskResponse.class,
                    "Qwen Wan",
                    "x-request-id"
            );
            WanTaskResponse response = poll.body();
            WanTaskOutput output = response == null ? null : response.output();
            String requestId = firstNonBlank(
                    response == null ? null : response.requestId(),
                    poll.providerRequestId(),
                    initialRequestId
            );
            String status = output == null ? null : output.taskStatus();
            if (status == null || status.isBlank()) {
                throw new AiProviderResponseException(
                        "Qwen Wan returned an empty task status",
                        taskId,
                        requestId
                );
            }
            switch (status) {
                case "SUCCEEDED" -> {
                    return response;
                }
                case "PENDING", "RUNNING" -> {
                    if (System.nanoTime() >= deadline) {
                        throw new AiProviderResponseException(
                                "Qwen Wan task timed out",
                                taskId,
                                requestId
                        );
                    }
                }
                case "UNKNOWN" -> {
                    if (expiredOnUnknown) {
                        throw new VideoArtifactExpiredException(
                                "Qwen Wan task is expired or no longer available"
                        );
                    }
                    throw new AiProviderResponseException(
                            "Qwen Wan task status is unknown",
                            taskId,
                            requestId
                    );
                }
                case "FAILED", "CANCELED" -> throw new VideoProviderTaskFailedException(
                        "Qwen Wan task " + status.toLowerCase() + ": " + safeProviderMessage(
                                firstNonBlank(output.message(), response.message())
                        ),
                        taskId,
                        requestId
                );
                default -> throw new AiProviderResponseException(
                        "Qwen Wan returned unsupported task status: " + safeProviderMessage(status),
                        taskId,
                        requestId
                );
            }
        }
    }

    private static long deadline(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException("Qwen Wan poll timeout must be positive");
        }
        return System.nanoTime() + timeout.toNanos();
    }

    private static void waitForPoll(
            Duration interval,
            long deadline,
            String taskId,
            String requestId
    ) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalStateException("Qwen Wan poll interval must be positive");
        }
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new AiProviderResponseException(
                    "Qwen Wan task timed out",
                    taskId,
                    requestId
            );
        }
        try {
            Thread.sleep(Duration.ofNanos(Math.min(interval.toNanos(), remainingNanos)));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderResponseException(
                    "Qwen Wan polling was interrupted",
                    taskId,
                    requestId,
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

    private record WanVideoRequest(String model, WanVideoInput input, WanVideoParameters parameters) {
    }

    private record WanVideoInput(String prompt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record WanVideoParameters(
            String size,
            String resolution,
            String ratio,
            Integer duration,
            @JsonProperty("prompt_extend") boolean promptExtend,
            boolean watermark
    ) {
    }

    private record WanTaskResponse(
            WanTaskOutput output,
            @JsonProperty("request_id") String requestId,
            String code,
            String message
    ) {
    }

    private record WanTaskOutput(
            @JsonProperty("task_id") String taskId,
            @JsonProperty("task_status") String taskStatus,
            @JsonProperty("video_url") String videoUrl,
            String code,
            String message
    ) {
    }

}
