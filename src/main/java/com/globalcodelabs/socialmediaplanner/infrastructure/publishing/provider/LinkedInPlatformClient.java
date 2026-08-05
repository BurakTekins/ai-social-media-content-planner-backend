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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkedInPlatformClient implements SocialPlatformClient {

    private static final String PROVIDER_NAME = "linkedin";
    private static final String RESTLI_PROTOCOL_VERSION = "2.0.0";

    private final PublishingProperties properties;
    private final PublishingRestClientFactory restClientFactory;
    private final MediaContentLoader mediaContentLoader;

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.LINKEDIN;
    }

    @Override
    public PublishContentResult publish(PublishContentRequest request) {
        validateRequest(request);
        PublishingProperties.Provider provider = properties.requireProvider(PROVIDER_NAME);
        String baseUrl = properties.requireBaseUrl(PROVIDER_NAME);
        String version = properties.requireVersion(PROVIDER_NAME);
        String owner = requireOwner(request.credential());
        long startedAt = System.nanoTime();
        MdcUtil.putProvider(PROVIDER_NAME);
        try {
            log.info("Social platform call started operation=publish contentId={}", request.contentId());
            String mediaUrn = uploadMedia(request, baseUrl, version, owner, provider);
            String externalPostId = createPost(request, baseUrl, version, owner, mediaUrn);
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
            throw new IllegalStateException("LinkedIn API request failed", exception);
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

    private String uploadMedia(
            PublishContentRequest request,
            String baseUrl,
            String version,
            String owner,
            PublishingProperties.Provider provider
    ) {
        List<PublishMedia> images = mediaOfType(request, MediaType.IMAGE);
        List<PublishMedia> videos = mediaOfType(request, MediaType.VIDEO);
        if (!images.isEmpty() && !videos.isEmpty()) {
            throw new IllegalArgumentException("LinkedIn mixed image and video publishing is not supported");
        }
        if (images.size() > 1 || videos.size() > 1) {
            throw new IllegalArgumentException("LinkedIn publishing supports at most one media item");
        }
        if (!images.isEmpty()) {
            return uploadImage(images.getFirst(), request.credential(), baseUrl, version, owner);
        }
        if (!videos.isEmpty()) {
            return uploadVideo(videos.getFirst(), request.credential(), baseUrl, version, owner, provider);
        }
        return null;
    }

    private String uploadImage(
            PublishMedia media,
            PlatformCredential credential,
            String baseUrl,
            String version,
            String owner
    ) {
        ImageInitializeResponse response = restClientFactory.forBaseUrl(baseUrl)
                .post()
                .uri(restClientFactory.endpoint(baseUrl, "rest/images?action=initializeUpload"))
                .header(HttpHeaders.AUTHORIZATION, bearer(credential))
                .header("Linkedin-Version", version)
                .header("X-Restli-Protocol-Version", RESTLI_PROTOCOL_VERSION)
                .body(new ImageInitializeRequest(new ImageInitializePayload(owner)))
                .retrieve()
                .body(ImageInitializeResponse.class);
        ImageInitializeValue value = requireImageInitializeValue(response);
        MediaContent content = mediaContentLoader.load(media);
        try {
            restClientFactory.forBaseUrl(baseUrl)
                    .put()
                    .uri(requireHttpsUploadUrl(value.uploadUrl()))
                    .header(HttpHeaders.AUTHORIZATION, bearer(credential))
                    .contentType(org.springframework.http.MediaType.parseMediaType(content.contentType()))
                    .body(content.bytes())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IllegalStateException("LinkedIn image upload failed");
        }
        return value.image();
    }

    private String uploadVideo(
            PublishMedia media,
            PlatformCredential credential,
            String baseUrl,
            String version,
            String owner,
            PublishingProperties.Provider provider
    ) {
        MediaContent content = mediaContentLoader.load(media);
        byte[] videoBytes = content.bytes();
        VideoInitializeResponse response = restClientFactory.forBaseUrl(baseUrl)
                .post()
                .uri(restClientFactory.endpoint(baseUrl, "rest/videos?action=initializeUpload"))
                .header(HttpHeaders.AUTHORIZATION, bearer(credential))
                .header("Linkedin-Version", version)
                .header("X-Restli-Protocol-Version", RESTLI_PROTOCOL_VERSION)
                .body(new VideoInitializeRequest(new VideoInitializePayload(
                        owner, videoBytes.length, false, false
                )))
                .retrieve()
                .body(VideoInitializeResponse.class);
        VideoInitializeValue value = requireVideoInitializeValue(response);
        List<String> uploadedPartIds = uploadVideoParts(value.uploadInstructions(), videoBytes);
        restClientFactory.forBaseUrl(baseUrl)
                .post()
                .uri(restClientFactory.endpoint(baseUrl, "rest/videos?action=finalizeUpload"))
                .header(HttpHeaders.AUTHORIZATION, bearer(credential))
                .header("Linkedin-Version", version)
                .header("X-Restli-Protocol-Version", RESTLI_PROTOCOL_VERSION)
                .body(new VideoFinalizeRequest(new VideoFinalizePayload(
                        value.video(), value.uploadToken(), uploadedPartIds
                )))
                .retrieve()
                .toBodilessEntity();
        waitUntilAvailable(
                baseUrl, version, credential, "videos", value.video(),
                provider.getPollInterval(), provider.getPollTimeout()
        );
        return value.video();
    }

    private List<String> uploadVideoParts(
            List<VideoUploadInstruction> uploadInstructions,
            byte[] videoBytes
    ) {
        if (uploadInstructions == null || uploadInstructions.isEmpty()) {
            throw new IllegalStateException("LinkedIn did not return video upload instructions");
        }
        List<String> uploadedPartIds = new ArrayList<>(uploadInstructions.size());
        for (VideoUploadInstruction instruction : uploadInstructions) {
            if (instruction == null
                    || instruction.firstByte() < 0
                    || instruction.lastByte() < instruction.firstByte()
                    || instruction.lastByte() >= videoBytes.length
                    || instruction.lastByte() > Integer.MAX_VALUE) {
                throw new IllegalStateException("LinkedIn returned an invalid video upload byte range");
            }
            byte[] part = Arrays.copyOfRange(
                    videoBytes,
                    Math.toIntExact(instruction.firstByte()),
                    Math.toIntExact(instruction.lastByte() + 1)
            );
            ResponseEntity<Void> response;
            try {
                response = restClientFactory.forBaseUrl("https://upload.linkedin.com")
                        .put()
                        .uri(requireHttpsUploadUrl(instruction.uploadUrl()))
                        .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                        .body(part)
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientException exception) {
                throw new IllegalStateException("LinkedIn video part upload failed");
            }
            String etag = response.getHeaders().getFirst(HttpHeaders.ETAG);
            if (etag == null || etag.isBlank()) {
                throw new IllegalStateException("LinkedIn video upload response is missing ETag");
            }
            uploadedPartIds.add(stripQuotes(etag.trim()));
        }
        return uploadedPartIds;
    }

    private void waitUntilAvailable(
            String baseUrl,
            String version,
            PlatformCredential credential,
            String resource,
            String mediaUrn,
            Duration pollInterval,
            Duration pollTimeout
    ) {
        long deadline = System.nanoTime() + pollTimeout.toNanos();
        String encodedUrn = UriUtils.encodePathSegment(mediaUrn, StandardCharsets.UTF_8);
        while (System.nanoTime() < deadline) {
            MediaStatusResponse response = restClientFactory.forBaseUrl(baseUrl)
                    .get()
                    .uri(restClientFactory.endpoint(baseUrl, "rest/" + resource + "/" + encodedUrn))
                    .header(HttpHeaders.AUTHORIZATION, bearer(credential))
                    .header("Linkedin-Version", version)
                    .header("X-Restli-Protocol-Version", RESTLI_PROTOCOL_VERSION)
                    .retrieve()
                    .body(MediaStatusResponse.class);
            String status = response == null ? null : response.status();
            if ("AVAILABLE".equals(status)) {
                return;
            }
            if ("PROCESSING_FAILED".equals(status)) {
                throw new IllegalStateException("LinkedIn media processing failed");
            }
            sleep(pollInterval);
        }
        throw new IllegalStateException("LinkedIn media processing timed out");
    }

    private String createPost(
            PublishContentRequest request,
            String baseUrl,
            String version,
            String owner,
            String mediaUrn
    ) {
        PostContent content = mediaUrn == null ? null : new PostContent(new PostMedia(mediaUrn));
        ResponseEntity<Void> response = restClientFactory.forBaseUrl(baseUrl)
                .post()
                .uri(restClientFactory.endpoint(baseUrl, "rest/posts"))
                .header(HttpHeaders.AUTHORIZATION, bearer(request.credential()))
                .header("Linkedin-Version", version)
                .header("X-Restli-Protocol-Version", RESTLI_PROTOCOL_VERSION)
                .body(new PostRequest(
                        owner,
                        request.formattedText(),
                        "PUBLIC",
                        new PostDistribution("MAIN_FEED", List.of(), List.of()),
                        content,
                        "PUBLISHED",
                        false
                ))
                .retrieve()
                .toBodilessEntity();
        String externalPostId = response.getHeaders().getFirst("x-restli-id");
        if (externalPostId == null || externalPostId.isBlank()) {
            throw new IllegalStateException("LinkedIn post response is missing x-restli-id");
        }
        return externalPostId;
    }

    private static void validateRequest(PublishContentRequest request) {
        if (request.platform() != Platform.LINKEDIN || request.contentType() != ContentType.POST) {
            throw new IllegalArgumentException("LinkedIn client only supports LinkedIn posts");
        }
        if (!PROVIDER_NAME.equals(request.credential().providerName())) {
            throw new IllegalArgumentException("LinkedIn credential provider must be linkedin");
        }
    }

    private static String requireOwner(PlatformCredential credential) {
        String owner = credential.accountIdentifier();
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("LinkedIn account identifier is required");
        }
        if (!owner.startsWith("urn:li:person:") && !owner.startsWith("urn:li:organization:")) {
            throw new IllegalArgumentException("LinkedIn account identifier must be a person or organization URN");
        }
        return owner;
    }

    private static List<PublishMedia> mediaOfType(
            PublishContentRequest request,
            MediaType mediaType
    ) {
        return request.media().stream()
                .filter(media -> media.mediaType() == mediaType)
                .toList();
    }

    private static ImageInitializeValue requireImageInitializeValue(ImageInitializeResponse response) {
        if (response == null
                || response.value() == null
                || response.value().uploadUrl() == null
                || response.value().uploadUrl().isBlank()
                || response.value().image() == null
                || response.value().image().isBlank()) {
            throw new IllegalStateException("LinkedIn returned an invalid image upload initialization response");
        }
        return response.value();
    }

    private static VideoInitializeValue requireVideoInitializeValue(VideoInitializeResponse response) {
        if (response == null
                || response.value() == null
                || response.value().video() == null
                || response.value().video().isBlank()
                || response.value().uploadToken() == null) {
            throw new IllegalStateException("LinkedIn returned an invalid video upload initialization response");
        }
        return response.value();
    }

    private static URI requireHttpsUploadUrl(String uploadUrl) {
        URI uri;
        try {
            uri = URI.create(uploadUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Platform returned an invalid media upload URL");
        }
        if (!uri.isAbsolute()
                || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null) {
            throw new IllegalStateException("Platform returned an invalid media upload URL");
        }
        return uri;
    }

    private static String bearer(PlatformCredential credential) {
        return "Bearer " + credential.accessToken();
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LinkedIn media processing wait was interrupted");
        }
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record ImageInitializeRequest(
            @JsonProperty("initializeUploadRequest") ImageInitializePayload initializeUploadRequest
    ) {
    }

    private record ImageInitializePayload(String owner) {
    }

    private record ImageInitializeResponse(ImageInitializeValue value) {
    }

    private record ImageInitializeValue(String uploadUrl, String image) {
    }

    private record VideoInitializeRequest(
            @JsonProperty("initializeUploadRequest") VideoInitializePayload initializeUploadRequest
    ) {
    }

    private record VideoInitializePayload(
            String owner,
            long fileSizeBytes,
            boolean uploadCaptions,
            boolean uploadThumbnail
    ) {
    }

    private record VideoInitializeResponse(VideoInitializeValue value) {
    }

    private record VideoInitializeValue(
            String video,
            String uploadToken,
            List<VideoUploadInstruction> uploadInstructions
    ) {
    }

    private record VideoUploadInstruction(
            long firstByte,
            long lastByte,
            String uploadUrl
    ) {
    }

    private record VideoFinalizeRequest(
            @JsonProperty("finalizeUploadRequest") VideoFinalizePayload finalizeUploadRequest
    ) {
    }

    private record VideoFinalizePayload(
            String video,
            String uploadToken,
            List<String> uploadedPartIds
    ) {
    }

    private record MediaStatusResponse(String status) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record PostRequest(
            String author,
            String commentary,
            String visibility,
            PostDistribution distribution,
            PostContent content,
            String lifecycleState,
            boolean isReshareDisabledByAuthor
    ) {
    }

    private record PostDistribution(
            String feedDistribution,
            List<String> targetEntities,
            List<String> thirdPartyDistributionChannels
    ) {
    }

    private record PostContent(PostMedia media) {
    }

    private record PostMedia(String id) {
    }
}
