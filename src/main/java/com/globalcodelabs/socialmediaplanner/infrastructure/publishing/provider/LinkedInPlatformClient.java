package com.globalcodelabs.socialmediaplanner.infrastructure.publishing.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PlatformCredential;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.DefinitivePublishingException;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishContentRequest;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishMedia;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.SocialPlatformClient;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.MediaType;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishingProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishingRestClientFactory;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.media.MediaContentLoader;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.StoredMediaContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkedInPlatformClient implements SocialPlatformClient {

    private static final String PROVIDER_NAME = "linkedin";
    private static final String RESTLI_PROTOCOL_VERSION = "2.0.0";
    private static final String POSTS_PATH = "rest/posts";
    private static final String IMAGES_PATH = "rest/images";
    private static final String VIDEOS_PATH = "rest/videos";
    private static final String INITIALIZE_IMAGE_UPLOAD_PATH = IMAGES_PATH + "?action=initializeUpload";
    private static final String INITIALIZE_VIDEO_UPLOAD_PATH = VIDEOS_PATH + "?action=initializeUpload";
    private static final String FINALIZE_VIDEO_UPLOAD_PATH = VIDEOS_PATH + "?action=finalizeUpload";
    private static final Pattern API_VERSION_PATTERN = Pattern.compile("\\d{6}");

    private final PublishingProperties properties;
    private final PublishingRestClientFactory restClientFactory;
    private final MediaContentLoader mediaContentLoader;

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.LINKEDIN;
    }

    @Override
    public String publish(PublishContentRequest request) {
        validateRequest(request);
        PublishingProperties.Provider provider = properties.requireProvider(PROVIDER_NAME);
        String baseUrl = properties.requireBaseUrl(PROVIDER_NAME);
        String version = requireApiVersion(properties.requireVersion(PROVIDER_NAME));
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
            return externalPostId;
        } catch (RestClientException exception) {
            logPublishFailure(request.contentId(), startedAt, exception);
            throw new IllegalStateException("LinkedIn API request failed", exception);
        } catch (DefinitivePublishingException exception) {
            log.warn(
                    "Social platform call rejected operation=publish contentId={} durationMs={} providerCode={}",
                    request.contentId(), elapsedMilliseconds(startedAt), exception.providerCode()
            );
            throw exception;
        } catch (RuntimeException exception) {
            log.error(
                    "Social platform call failed operation=publish contentId={} durationMs={} errorType={}",
                    request.contentId(), elapsedMilliseconds(startedAt),
                    exception.getClass().getSimpleName(),
                    exception
            );
            throw exception;
        } finally {
            MdcUtil.removeProvider();
        }
    }

    @Override
    public boolean isPublished(String externalPostId, PlatformCredential credential) {
        if (externalPostId == null
                || (!externalPostId.startsWith("urn:li:share:")
                && !externalPostId.startsWith("urn:li:ugcPost:"))) {
            throw new IllegalArgumentException("LinkedIn post id is invalid");
        }
        log.debug(
                "LinkedIn publication accepted from create response externalPostId={}",
                externalPostId
        );
        return true;
    }

    private static void logPublishFailure(UUID contentId, long startedAt, RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException
                && responseException.getStatusCode().is4xxClientError()) {
            log.warn("Social platform call rejected operation=publish contentId={} durationMs={} errorType={} httpStatus={}",
                    contentId, elapsedMilliseconds(startedAt), exception.getClass().getSimpleName(),
                    responseException.getStatusCode().value());
        } else {
            log.error("Social platform call failed operation=publish contentId={} durationMs={} errorType={}",
                    contentId, elapsedMilliseconds(startedAt),
                    exception.getClass().getSimpleName(), exception);
        }
    }

    private String uploadMedia(
            PublishContentRequest request,
            String baseUrl,
            String version,
            String owner,
            PublishingProperties.Provider provider
    ) {
        List<PublishMedia> images = request.mediaOfType(MediaType.IMAGE);
        List<PublishMedia> videos = request.mediaOfType(MediaType.VIDEO);
        if (!images.isEmpty() && !videos.isEmpty()) {
            throw new IllegalArgumentException("LinkedIn mixed image and video publishing is not supported");
        }
        if (images.size() > 1 || videos.size() > 1) {
            throw new IllegalArgumentException("LinkedIn publishing supports at most one media item");
        }
        if (!images.isEmpty()) {
            return uploadImage(
                    images.getFirst(), request.credential(), baseUrl, version, owner, provider
            );
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
            String owner,
            PublishingProperties.Provider provider
    ) {
        ImageInitializeResponse response = restClientFactory.forBaseUrl(baseUrl)
                .post()
                .uri(restClientFactory.endpoint(baseUrl, INITIALIZE_IMAGE_UPLOAD_PATH))
                .header(HttpHeaders.AUTHORIZATION, credential.authorizationHeader())
                .header("Linkedin-Version", version)
                .header("X-Restli-Protocol-Version", RESTLI_PROTOCOL_VERSION)
                .body(new ImageInitializeRequest(new ImageInitializePayload(owner)))
                .retrieve()
                .body(ImageInitializeResponse.class);
        ImageInitializeValue value = requireImageInitializeValue(response);
        StoredMediaContent content = mediaContentLoader.load(media);
        try {
            restClientFactory.forBaseUrl(baseUrl)
                    .put()
                    .uri(requireHttpsUploadUrl(value.uploadUrl()))
                    .header(HttpHeaders.AUTHORIZATION, credential.authorizationHeader())
                    .contentType(org.springframework.http.MediaType.parseMediaType(content.contentType()))
                    .body(content.bytes())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IllegalStateException("LinkedIn image upload failed", exception);
        }
        waitUntilAvailable(
                baseUrl,
                version,
                credential,
                IMAGES_PATH + "/" + encodedUrn(value.image()),
                provider.getPollInterval(),
                provider.getPollTimeout()
        );
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
        StoredMediaContent content = mediaContentLoader.load(media);
        byte[] videoBytes = content.bytes();
        VideoInitializeResponse response = restClientFactory.forBaseUrl(baseUrl)
                .post()
                .uri(restClientFactory.endpoint(baseUrl, INITIALIZE_VIDEO_UPLOAD_PATH))
                .header(HttpHeaders.AUTHORIZATION, credential.authorizationHeader())
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
                .uri(restClientFactory.endpoint(baseUrl, FINALIZE_VIDEO_UPLOAD_PATH))
                .header(HttpHeaders.AUTHORIZATION, credential.authorizationHeader())
                .header("Linkedin-Version", version)
                .header("X-Restli-Protocol-Version", RESTLI_PROTOCOL_VERSION)
                .body(new VideoFinalizeRequest(new VideoFinalizePayload(
                        value.video(), value.uploadToken(), uploadedPartIds
                )))
                .retrieve()
                .toBodilessEntity();
        waitUntilAvailable(
                baseUrl,
                version,
                credential,
                VIDEOS_PATH + "/" + encodedUrn(value.video()),
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
                throw new IllegalStateException("LinkedIn video part upload failed", exception);
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
            String statusPath,
            Duration pollInterval,
            Duration pollTimeout
    ) {
        long deadline = System.nanoTime() + pollTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            MediaStatusResponse response = restClientFactory.forBaseUrl(baseUrl)
                    .get()
                    .uri(restClientFactory.endpoint(baseUrl, statusPath))
                    .header(HttpHeaders.AUTHORIZATION, credential.authorizationHeader())
                    .header("Linkedin-Version", version)
                    .header("X-Restli-Protocol-Version", RESTLI_PROTOCOL_VERSION)
                    .retrieve()
                    .body(MediaStatusResponse.class);
            String status = response == null ? null : response.status();
            if ("AVAILABLE".equals(status)) {
                return;
            }
            if ("PROCESSING_FAILED".equals(status)) {
                throw new DefinitivePublishingException("LINKEDIN_MEDIA_PROCESSING_FAILED");
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
                .uri(restClientFactory.endpoint(baseUrl, POSTS_PATH))
                .header(HttpHeaders.AUTHORIZATION, request.credential().authorizationHeader())
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

    private static String requireApiVersion(String version) {
        if (!API_VERSION_PATTERN.matcher(version).matches()) {
            throw new IllegalStateException("LinkedIn API version must use YYYYMM format");
        }
        return version;
    }

    private static String encodedUrn(String urn) {
        return UriUtils.encodePathSegment(urn, StandardCharsets.UTF_8);
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
