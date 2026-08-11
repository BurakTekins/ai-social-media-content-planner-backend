package com.globalcodelabs.socialmediaplanner.infrastructure.publishing.provider;

import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PlatformCredential;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.DefinitivePublishingException;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishContentRequest;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishContentResult;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishMedia;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.SocialPlatformClient;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishingProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.config.PublishingRestClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstagramPlatformClient implements SocialPlatformClient {

    private static final String PROVIDER_NAME = "instagram";

    private final PublishingProperties properties;
    private final PublishingRestClientFactory restClientFactory;

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.INSTAGRAM;
    }

    @Override
    public PublishContentResult publish(PublishContentRequest request) {
        validateRequest(request);
        String baseUrl = properties.requireBaseUrl(PROVIDER_NAME);
        String version = properties.requireVersion(PROVIDER_NAME);
        PublishingProperties.Provider provider = properties.requireProvider(PROVIDER_NAME);
        String accountIdentifier = requireAccountIdentifier(request.credential());
        long startedAt = System.nanoTime();
        MdcUtil.putProvider(PROVIDER_NAME);
        try {
            log.info("Social platform call started operation=publish contentId={}", request.contentId());
            String containerId = createContainer(
                    request, baseUrl, version, accountIdentifier
            );
            waitUntilReady(
                    baseUrl, version, request.credential(), containerId,
                    provider.getPollInterval(), provider.getPollTimeout()
            );
            String externalPostId = publishContainer(
                    baseUrl, version, request.credential(), accountIdentifier, containerId
            );
            log.info(
                    "Social platform call completed operation=publish contentId={} durationMs={}",
                    request.contentId(), elapsedMilliseconds(startedAt)
            );
            return new PublishContentResult(externalPostId);
        } catch (RestClientException exception) {
            logPublishFailure(request.contentId(), startedAt, exception);
            throw new IllegalStateException("Instagram API request failed", exception);
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
        String baseUrl = properties.requireBaseUrl(PROVIDER_NAME);
        String version = properties.requireVersion(PROVIDER_NAME);
        long startedAt = System.nanoTime();
        try {
            log.info("Social platform call started operation=verify-publication externalPostId={}", externalPostId);
            ContainerResponse response = restClientFactory.forBaseUrl(baseUrl)
                    .get()
                    .uri(restClientFactory.endpoint(
                            baseUrl, version + "/" + externalPostId + "?fields=id"
                    ))
                    .header(HttpHeaders.AUTHORIZATION, bearer(credential))
                    .retrieve()
                    .body(ContainerResponse.class);
            boolean published = response != null && externalPostId.equals(response.id());
            log.info("Social platform call completed operation=verify-publication externalPostId={} published={} durationMs={}",
                    externalPostId, published, elapsedMilliseconds(startedAt));
            return published;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                log.info("Social platform call completed operation=verify-publication externalPostId={} published=false httpStatus=404 durationMs={}",
                        externalPostId, elapsedMilliseconds(startedAt));
                return false;
            }
            if (exception.getStatusCode().is4xxClientError()) {
                log.warn("Social platform call rejected operation=verify-publication externalPostId={} httpStatus={} durationMs={}",
                        externalPostId, exception.getStatusCode().value(), elapsedMilliseconds(startedAt));
            } else {
                log.error("Social platform call failed operation=verify-publication externalPostId={} httpStatus={} durationMs={}",
                        externalPostId, exception.getStatusCode().value(),
                        elapsedMilliseconds(startedAt), exception);
            }
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Social platform call failed operation=verify-publication externalPostId={} errorType={} durationMs={}",
                    externalPostId, exception.getClass().getSimpleName(),
                    elapsedMilliseconds(startedAt), exception);
            throw exception;
        }
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

    private String createContainer(
            PublishContentRequest request,
            String baseUrl,
            String version,
            String accountIdentifier
    ) {
        MultiValueMap<String, String> form = switch (request.contentType()) {
            case POST -> imagePostForm(request);
            case REEL -> reelForm(request);
            default -> throw new IllegalArgumentException("Instagram only supports posts and reels");
        };
        ContainerResponse response = restClientFactory.forBaseUrl(baseUrl)
                .post()
                .uri(restClientFactory.endpoint(
                        baseUrl, version + "/" + accountIdentifier + "/media"
                ))
                .header(HttpHeaders.AUTHORIZATION, bearer(request.credential()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(ContainerResponse.class);
        return requireId(response, "Instagram did not return a creation container id");
    }

    private MultiValueMap<String, String> imagePostForm(PublishContentRequest request) {
        List<PublishMedia> images = mediaOfType(request, com.globalcodelabs.socialmediaplanner.domain.enums.MediaType.IMAGE);
        List<PublishMedia> videos = mediaOfType(request, com.globalcodelabs.socialmediaplanner.domain.enums.MediaType.VIDEO);
        if (!videos.isEmpty()) {
            throw new IllegalArgumentException("Instagram POST does not support video media");
        }
        if (images.size() != 1) {
            throw new IllegalArgumentException("Instagram POST requires exactly one image");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("image_url", requirePublicHttpsUrl(images.getFirst().publicUrl(), "Instagram image"));
        form.add("caption", request.formattedText());
        return form;
    }

    private MultiValueMap<String, String> reelForm(PublishContentRequest request) {
        List<PublishMedia> images = mediaOfType(request, com.globalcodelabs.socialmediaplanner.domain.enums.MediaType.IMAGE);
        List<PublishMedia> videos = mediaOfType(request, com.globalcodelabs.socialmediaplanner.domain.enums.MediaType.VIDEO);
        if (videos.size() != 1) {
            throw new IllegalArgumentException("Instagram REEL requires exactly one video");
        }
        if (images.size() > 1) {
            throw new IllegalArgumentException("Instagram REEL supports at most one image cover");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("media_type", "REELS");
        form.add("video_url", requirePublicHttpsUrl(videos.getFirst().publicUrl(), "Instagram video"));
        form.add("caption", request.formattedText());
        form.add("share_to_feed", "true");
        if (!images.isEmpty()) {
            form.add("cover_url", requirePublicHttpsUrl(images.getFirst().publicUrl(), "Instagram cover image"));
        }
        return form;
    }

    private void waitUntilReady(
            String baseUrl,
            String version,
            PlatformCredential credential,
            String containerId,
            Duration pollInterval,
            Duration pollTimeout
    ) {
        long deadline = System.nanoTime() + pollTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            ContainerStatusResponse response = restClientFactory.forBaseUrl(baseUrl)
                    .get()
                    .uri(restClientFactory.endpoint(
                            baseUrl,
                            version + "/" + containerId + "?fields=status_code,status"
                    ))
                    .header(HttpHeaders.AUTHORIZATION, bearer(credential))
                    .retrieve()
                    .body(ContainerStatusResponse.class);
            String statusCode = response == null ? null : response.statusCode();
            if ("FINISHED".equals(statusCode)) {
                return;
            }
            if ("ERROR".equals(statusCode)) {
                throw new DefinitivePublishingException("INSTAGRAM_CONTAINER_ERROR");
            }
            if ("EXPIRED".equals(statusCode)) {
                throw new DefinitivePublishingException("INSTAGRAM_CONTAINER_EXPIRED");
            }
            sleep(pollInterval);
        }
        throw new IllegalStateException("Instagram media container processing timed out");
    }

    private String publishContainer(
            String baseUrl,
            String version,
            PlatformCredential credential,
            String accountIdentifier,
            String containerId
    ) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("creation_id", containerId);
        ContainerResponse response = restClientFactory.forBaseUrl(baseUrl)
                .post()
                .uri(restClientFactory.endpoint(
                        baseUrl, version + "/" + accountIdentifier + "/media_publish"
                ))
                .header(HttpHeaders.AUTHORIZATION, bearer(credential))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(ContainerResponse.class);
        return requireId(response, "Instagram did not return a published media id");
    }

    private static void validateRequest(PublishContentRequest request) {
        if (request.platform() != Platform.INSTAGRAM
                || (request.contentType() != ContentType.POST
                && request.contentType() != ContentType.REEL)) {
            throw new IllegalArgumentException("Instagram client only supports Instagram posts and reels");
        }
        if (!PROVIDER_NAME.equals(request.credential().providerName())) {
            throw new IllegalArgumentException("Instagram credential provider must be instagram");
        }
    }

    private static String requireAccountIdentifier(PlatformCredential credential) {
        String accountIdentifier = credential.accountIdentifier();
        if (accountIdentifier == null
                || accountIdentifier.isBlank()
                || !accountIdentifier.matches("\\d+")) {
            throw new IllegalArgumentException("Instagram account identifier must be a numeric account id");
        }
        return accountIdentifier;
    }

    private static List<PublishMedia> mediaOfType(
            PublishContentRequest request,
            com.globalcodelabs.socialmediaplanner.domain.enums.MediaType mediaType
    ) {
        return request.media().stream()
                .filter(media -> media.mediaType() == mediaType)
                .toList();
    }

    private static String requirePublicHttpsUrl(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " requires a public HTTPS URL");
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(fieldName + " URL is invalid");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException(fieldName + " requires a public HTTPS URL");
        }
        try {
            boolean privateAddress = Arrays.stream(InetAddress.getAllByName(uri.getHost()))
                    .anyMatch(InstagramPlatformClient::isPrivateAddress);
            if (privateAddress) {
                throw new IllegalArgumentException(fieldName + " URL cannot target a private network");
            }
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException(fieldName + " URL host could not be resolved");
        }
        return uri.toString();
    }

    private static String requireId(ContainerResponse response, String message) {
        if (response == null || response.id() == null || response.id().isBlank()) {
            throw new IllegalStateException(message);
        }
        return response.id();
    }

    private static String bearer(PlatformCredential credential) {
        return "Bearer " + credential.accessToken();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Instagram media processing wait was interrupted");
        }
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static boolean isPrivateAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isUniqueLocalIpv6(address);
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    private record ContainerResponse(String id) {
    }

    private record ContainerStatusResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("status_code") String statusCode,
            String status
    ) {
    }
}
