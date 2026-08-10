package com.globalcodelabs.socialmediaplanner.infrastructure.publishing.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.PlatformCredential;
import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.PublishContentRequest;
import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.PublishContentResult;
import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.PublishMedia;
import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.SocialPlatformClient;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.model.MediaType;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishingProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.config.PublishingRestClientFactory;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.media.MediaContent;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.media.MediaContentLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class TwitterPlatformClient implements SocialPlatformClient {

    private static final String PROVIDER_NAME = "twitter";
    private static final String VIDEO_MP4 = "video/mp4";
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final PublishingProperties properties;
    private final PublishingRestClientFactory restClientFactory;
    private final MediaContentLoader mediaContentLoader;

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.TWITTER;
    }

    @Override
    public PublishContentResult publish(PublishContentRequest request) {
        validateRequest(request);
        String baseUrl = properties.requireBaseUrl(PROVIDER_NAME);
        PublishingProperties.Provider provider = properties.requireProvider(PROVIDER_NAME);
        long startedAt = System.nanoTime();
        MdcUtil.putProvider(PROVIDER_NAME);
        try {
            log.info("Social platform call started operation=publish contentId={}", request.contentId());
            String mediaId = uploadMedia(request, baseUrl, provider);
            String externalPostId = createTweet(request, baseUrl, mediaId);
            log.info(
                    "Social platform call completed operation=publish contentId={} durationMs={}",
                    request.contentId(), elapsedMilliseconds(startedAt)
            );
            return new PublishContentResult(externalPostId);
        } catch (RestClientException exception) {
            log.error(
                    "Social platform call failed operation=publish contentId={} durationMs={} errorType={}",
                    request.contentId(), elapsedMilliseconds(startedAt),
                    exception.getClass().getSimpleName(), exception
            );
            throw new IllegalStateException("X API request failed", exception);
        } catch (RuntimeException exception) {
            log.error(
                    "Social platform call failed operation=publish contentId={} durationMs={} errorType={}",
                    request.contentId(), elapsedMilliseconds(startedAt),
                    exception.getClass().getSimpleName(), exception
            );
            throw exception;
        } finally {
            MdcUtil.removeProvider();
        }
    }

    @Override
    public boolean isPublished(String externalPostId, PlatformCredential credential) {
        String baseUrl = properties.requireBaseUrl(PROVIDER_NAME);
        try {
            TweetResponse response = restClientFactory.forBaseUrl(baseUrl)
                    .get()
                    .uri(restClientFactory.endpoint(baseUrl, "2/tweets/" + externalPostId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(credential))
                    .retrieve()
                    .body(TweetResponse.class);
            return response != null
                    && response.data() != null
                    && externalPostId.equals(response.data().id());
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return false;
            }
            throw exception;
        }
    }

    private String uploadMedia(
            PublishContentRequest request,
            String baseUrl,
            PublishingProperties.Provider provider
    ) {
        List<PublishMedia> images = mediaOfType(request, MediaType.IMAGE);
        List<PublishMedia> videos = mediaOfType(request, MediaType.VIDEO);
        if (!images.isEmpty() && !videos.isEmpty()) {
            throw new IllegalArgumentException("X mixed image and video publishing is not supported");
        }
        if (images.size() > 1 || videos.size() > 1) {
            throw new IllegalArgumentException("X publishing supports at most one media item");
        }
        if (!images.isEmpty()) {
            return uploadImage(images.getFirst(), request.credential(), baseUrl);
        }
        if (!videos.isEmpty()) {
            return uploadVideo(videos.getFirst(), request.credential(), baseUrl, provider);
        }
        return null;
    }

    private String uploadImage(
            PublishMedia media,
            PlatformCredential credential,
            String baseUrl
    ) {
        MediaContent content = mediaContentLoader.load(media);
        if (!SUPPORTED_IMAGE_TYPES.contains(content.contentType())) {
            throw new IllegalArgumentException("X image publishing requires JPEG, PNG or WebP media");
        }
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part(
                        "media",
                        namedResource(content.bytes(), filenameFor(content.contentType(), "image"))
                )
                .contentType(org.springframework.http.MediaType.parseMediaType(content.contentType()));
        bodyBuilder.part("media_category", "tweet_image");
        bodyBuilder.part("media_type", content.contentType());
        bodyBuilder.part("shared", "false");
        MediaUploadResponse response = restClientFactory.forBaseUrl(baseUrl)
                .post()
                .uri(restClientFactory.endpoint(baseUrl, "2/media/upload"))
                .header(HttpHeaders.AUTHORIZATION, bearer(credential))
                .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                .body(bodyBuilder.build())
                .retrieve()
                .body(MediaUploadResponse.class);
        return requireMediaId(response);
    }

    private String uploadVideo(
            PublishMedia media,
            PlatformCredential credential,
            String baseUrl,
            PublishingProperties.Provider provider
    ) {
        MediaContent content = mediaContentLoader.load(media);
        if (!VIDEO_MP4.equals(content.contentType())) {
            throw new IllegalArgumentException("X video publishing requires video/mp4 media");
        }
        byte[] videoBytes = content.bytes();
        int chunkSize = provider.getUploadChunkSizeBytes();
        int segmentCount = (int) Math.ceil((double) videoBytes.length / chunkSize);
        if (segmentCount > 1_000) {
            throw new IllegalArgumentException("X video requires more than 1000 upload segments");
        }
        MultipartBodyBuilder initializeBody = new MultipartBodyBuilder();
        initializeBody.part("command", "INIT");
        initializeBody.part("media_type", content.contentType());
        initializeBody.part("total_bytes", Long.toString(videoBytes.length));
        initializeBody.part("media_category", "tweet_video");
        MediaUploadResponse initializeResponse = restClientFactory.forBaseUrl(baseUrl)
                .post()
                .uri(restClientFactory.endpoint(baseUrl, "2/media/upload"))
                .header(HttpHeaders.AUTHORIZATION, bearer(credential))
                .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                .body(initializeBody.build())
                .retrieve()
                .body(MediaUploadResponse.class);
        String mediaId = requireMediaId(initializeResponse);
        appendVideoSegments(
                credential, baseUrl, mediaId, videoBytes, chunkSize, content.contentType()
        );
        MultipartBodyBuilder finalizeBody = new MultipartBodyBuilder();
        finalizeBody.part("command", "FINALIZE");
        finalizeBody.part("media_id", mediaId);
        MediaUploadResponse finalizeResponse = restClientFactory.forBaseUrl(baseUrl)
                .post()
                .uri(restClientFactory.endpoint(baseUrl, "2/media/upload"))
                .header(HttpHeaders.AUTHORIZATION, bearer(credential))
                .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                .body(finalizeBody.build())
                .retrieve()
                .body(MediaUploadResponse.class);
        waitUntilProcessed(
                credential, baseUrl, mediaId,
                requireMediaData(finalizeResponse).processingInfo(),
                provider.getPollInterval(), provider.getPollTimeout()
        );
        return mediaId;
    }

    private void appendVideoSegments(
            PlatformCredential credential,
            String baseUrl,
            String mediaId,
            byte[] videoBytes,
            int chunkSize,
            String contentType
    ) {
        int segmentIndex = 0;
        for (int offset = 0; offset < videoBytes.length; offset += chunkSize) {
            int end = Math.min(offset + chunkSize, videoBytes.length);
            byte[] chunk = Arrays.copyOfRange(videoBytes, offset, end);
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("command", "APPEND");
            bodyBuilder.part("media_id", mediaId);
            bodyBuilder.part("segment_index", Integer.toString(segmentIndex));
            bodyBuilder.part(
                            "media",
                            namedResource(chunk, filenameFor(contentType, "segment-" + segmentIndex))
                    )
                    .contentType(org.springframework.http.MediaType.parseMediaType(contentType));
            restClientFactory.forBaseUrl(baseUrl)
                    .post()
                    .uri(restClientFactory.endpoint(baseUrl, "2/media/upload"))
                    .header(HttpHeaders.AUTHORIZATION, bearer(credential))
                    .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                    .body(bodyBuilder.build())
                    .retrieve()
                    .toBodilessEntity();
            segmentIndex++;
        }
    }

    private void waitUntilProcessed(
            PlatformCredential credential,
            String baseUrl,
            String mediaId,
            ProcessingInfo initialProcessingInfo,
            Duration defaultPollInterval,
            Duration pollTimeout
    ) {
        if (initialProcessingInfo == null) {
            return;
        }
        long deadline = System.nanoTime() + pollTimeout.toNanos();
        ProcessingInfo processingInfo = initialProcessingInfo;
        while (System.nanoTime() < deadline) {
            if ("succeeded".equals(processingInfo.state())) {
                return;
            }
            if ("failed".equals(processingInfo.state())) {
                throw new IllegalStateException("X media processing failed");
            }
            if (!"pending".equals(processingInfo.state())
                    && !"in_progress".equals(processingInfo.state())) {
                throw new IllegalStateException("X returned an unknown media processing state");
            }
            Duration waitDuration = processingInfo.checkAfterSeconds() == null
                    || processingInfo.checkAfterSeconds() <= 0
                    ? defaultPollInterval
                    : Duration.ofSeconds(processingInfo.checkAfterSeconds());
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                break;
            }
            if (waitDuration.toNanos() > remainingNanos) {
                waitDuration = Duration.ofNanos(remainingNanos);
            }
            sleep(waitDuration);
            if (System.nanoTime() >= deadline) {
                break;
            }
            MediaUploadResponse statusResponse = restClientFactory.forBaseUrl(baseUrl)
                    .get()
                    .uri(restClientFactory.endpoint(
                            baseUrl, "2/media/upload?command=STATUS&media_id=" + mediaId
                    ))
                    .header(HttpHeaders.AUTHORIZATION, bearer(credential))
                    .retrieve()
                    .body(MediaUploadResponse.class);
            processingInfo = requireMediaData(statusResponse).processingInfo();
            if (processingInfo == null) {
                return;
            }
        }
        throw new IllegalStateException("X media processing timed out");
    }

    private String createTweet(
            PublishContentRequest request,
            String baseUrl,
            String mediaId
    ) {
        TweetMedia tweetMedia = mediaId == null ? null : new TweetMedia(List.of(mediaId));
        Boolean madeWithAi = mediaId != null && request.media().stream()
                .anyMatch(media -> media.modelProvider() != null)
                ? Boolean.TRUE
                : null;
        TweetResponse response = restClientFactory.forBaseUrl(baseUrl)
                .post()
                .uri(restClientFactory.endpoint(baseUrl, "2/tweets"))
                .header(HttpHeaders.AUTHORIZATION, bearer(request.credential()))
                .body(new TweetRequest(request.formattedText(), tweetMedia, madeWithAi))
                .retrieve()
                .body(TweetResponse.class);
        if (response == null
                || response.data() == null
                || response.data().id() == null
                || response.data().id().isBlank()) {
            throw new IllegalStateException("X did not return a tweet id");
        }
        return response.data().id();
    }

    private static void validateRequest(PublishContentRequest request) {
        if (request.platform() != Platform.TWITTER || request.contentType() != ContentType.TWEET) {
            throw new IllegalArgumentException("X client only supports Twitter tweets");
        }
        if (!PROVIDER_NAME.equals(request.credential().providerName())) {
            throw new IllegalArgumentException("X credential provider must be twitter");
        }
    }

    private static List<PublishMedia> mediaOfType(
            PublishContentRequest request,
            com.globalcodelabs.socialmediaplanner.domain.model.MediaType mediaType
    ) {
        return request.media().stream()
                .filter(media -> media.mediaType() == mediaType)
                .toList();
    }

    private static String requireMediaId(MediaUploadResponse response) {
        MediaUploadData data = requireMediaData(response);
        if (data.id() == null || data.id().isBlank()) {
            throw new IllegalStateException("X did not return a media id");
        }
        return data.id();
    }

    private static MediaUploadData requireMediaData(MediaUploadResponse response) {
        if (response == null || response.data() == null) {
            throw new IllegalStateException("X returned an invalid media upload response");
        }
        return response.data();
    }

    private static ByteArrayResource namedResource(byte[] bytes, String filename) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private static String filenameFor(String contentType, String prefix) {
        return switch (contentType) {
            case "image/png" -> prefix + ".png";
            case "image/gif" -> prefix + ".gif";
            case "image/webp" -> prefix + ".webp";
            case "video/mp4" -> prefix + ".mp4";
            default -> prefix + ".jpg";
        };
    }

    private static String bearer(PlatformCredential credential) {
        return "Bearer " + credential.accessToken();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("X media processing wait was interrupted");
        }
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record MediaUploadResponse(MediaUploadData data) {
    }

    private record MediaUploadData(
            String id,
            @JsonProperty("processing_info") ProcessingInfo processingInfo
    ) {
    }

    private record ProcessingInfo(
            String state,
            @JsonProperty("check_after_secs") Integer checkAfterSeconds,
            ProcessingError error
    ) {
    }

    private record ProcessingError(Integer code, String name, String message) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record TweetRequest(
            String text,
            TweetMedia media,
            @JsonProperty("made_with_ai") Boolean madeWithAi
    ) {
    }

    private record TweetMedia(
            @JsonProperty("media_ids") List<String> mediaIds
    ) {
    }

    private record TweetResponse(TweetData data) {
    }

    private record TweetData(String id, String text) {
    }
}
