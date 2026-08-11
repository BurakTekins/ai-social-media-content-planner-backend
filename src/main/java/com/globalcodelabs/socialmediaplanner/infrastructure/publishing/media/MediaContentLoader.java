package com.globalcodelabs.socialmediaplanner.infrastructure.publishing.media;

import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishMedia;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.LocalDocumentStorage;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.StoredMediaContent;
import com.globalcodelabs.socialmediaplanner.domain.enums.MediaType;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishingProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

@Component
public class MediaContentLoader {

    private static final String USER_AGENT = "ai-social-media-content-planner/1.0";

    private final LocalDocumentStorage documentStorage;
    private final PublishingProperties properties;
    private final HttpClient httpClient;

    public MediaContentLoader(LocalDocumentStorage documentStorage, PublishingProperties properties) {
        this.documentStorage = documentStorage;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public MediaContent load(PublishMedia media) {
        if (media.publicUrl() == null || media.publicUrl().isBlank()) {
            return fromStorage(media);
        }
        return load(media.mediaType(), media.publicUrl());
    }

    public MediaContent load(MediaType mediaType, String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("Media source URL cannot be blank");
        }
        if (sourceUrl.startsWith("data:")) {
            return fromDataUri(mediaType, sourceUrl);
        }
        URI uri = validatePublicHttpsUrl(sourceUrl);
        return fromRemoteUrl(mediaType, uri);
    }

    public StoredMediaContent loadGenerated(MediaType mediaType, String sourceUrl) {
        MediaContent content = load(mediaType, sourceUrl);
        return new StoredMediaContent(content.contentType(), content.bytes());
    }

    private MediaContent fromStorage(PublishMedia media) {
        byte[] bytes = documentStorage.read(media.storageKey());
        ensureMaximumSize(bytes.length);
        return validatedContent(media.mediaType(), bytes, inferContentType(media.storageKey(), bytes));
    }

    private MediaContent fromDataUri(MediaType mediaType, String sourceUrl) {
        int separator = sourceUrl.indexOf(',');
        if (separator < 0) {
            throw new IllegalArgumentException("Media data URL is invalid");
        }
        String metadata = sourceUrl.substring("data:".length(), separator);
        if (!metadata.toLowerCase(Locale.ROOT).endsWith(";base64")) {
            throw new IllegalArgumentException("Media data URL must use Base64 encoding");
        }
        String contentType = metadata.substring(0, metadata.length() - ";base64".length());
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(
                    sourceUrl.substring(separator + 1).getBytes(StandardCharsets.US_ASCII)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Media data URL contains invalid Base64 data");
        }
        ensureMaximumSize(bytes.length);
        return validatedContent(mediaType, bytes, contentType);
    }

    private MediaContent fromRemoteUrl(MediaType mediaType, URI initialUri) {
        URI currentUri = initialUri;
        for (int redirectCount = 0; redirectCount <= properties.getMaxMediaRedirects(); redirectCount++) {
            HttpRequest request = HttpRequest.newBuilder(currentUri)
                    .timeout(properties.getReadTimeout())
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            try {
                HttpResponse<InputStream> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofInputStream()
                );
                try (InputStream responseBody = response.body()) {
                    if (isRedirect(response.statusCode())) {
                        if (redirectCount == properties.getMaxMediaRedirects()) {
                            throw new IllegalStateException("Media URL exceeded redirect limit");
                        }
                        String location = response.headers().firstValue("Location")
                                .orElseThrow(() -> new IllegalStateException(
                                        "Media URL redirect is missing Location header"
                                ));
                        currentUri = validatePublicHttpsUrl(currentUri.resolve(location).toString());
                        continue;
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException(
                                "Media URL returned HTTP status " + response.statusCode()
                        );
                    }
                    long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                    if (contentLength > properties.getMaxMediaBytes()) {
                        throw new IllegalArgumentException("Media exceeds configured size limit");
                    }
                    byte[] bytes = responseBody.readNBytes(properties.getMaxMediaBytes() + 1);
                    ensureMaximumSize(bytes.length);
                    String contentType = response.headers().firstValue("Content-Type")
                            .map(MediaContentLoader::normalizeContentType)
                            .orElse(null);
                    if (contentType == null || "application/octet-stream".equals(contentType)) {
                        contentType = inferContentType(currentUri.getPath(), bytes);
                    }
                    return validatedContent(mediaType, bytes, contentType);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Could not download media content");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Media download was interrupted");
            }
        }
        throw new IllegalStateException("Media URL exceeded redirect limit");
    }

    private MediaContent validatedContent(MediaType mediaType, byte[] bytes, String contentType) {
        String normalized = normalizeContentType(contentType);
        String expectedPrefix = mediaType == MediaType.IMAGE ? "image/" : "video/";
        if (!normalized.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException(
                    "Media content type does not match declared type " + mediaType
            );
        }
        return new MediaContent(bytes, normalized);
    }

    private void ensureMaximumSize(int size) {
        if (size > properties.getMaxMediaBytes()) {
            throw new IllegalArgumentException("Media exceeds configured size limit");
        }
    }

    private URI validatePublicHttpsUrl(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Media URL is invalid");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Media URL must use HTTPS");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Media URL host is invalid");
        }
        try {
            boolean privateAddress = Arrays.stream(InetAddress.getAllByName(uri.getHost()))
                    .anyMatch(MediaContentLoader::isPrivateAddress);
            if (privateAddress) {
                throw new IllegalArgumentException("Private network media URLs are not allowed");
            }
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Media URL host could not be resolved");
        }
        return uri;
    }

    private static String inferContentType(String source, byte[] bytes) {
        String normalizedSource = source == null ? "" : source.toLowerCase(Locale.ROOT);
        if (normalizedSource.endsWith(".png") || hasPrefix(bytes, 0x89, 0x50, 0x4E, 0x47)) {
            return "image/png";
        }
        if (normalizedSource.endsWith(".jpg") || normalizedSource.endsWith(".jpeg")
                || hasPrefix(bytes, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (normalizedSource.endsWith(".gif") || hasAsciiPrefix(bytes, "GIF8")) {
            return "image/gif";
        }
        if (normalizedSource.endsWith(".webp")
                || (bytes.length >= 12
                && hasAsciiPrefix(bytes, "RIFF")
                && "WEBP".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII)))) {
            return "image/webp";
        }
        if (normalizedSource.endsWith(".mp4")
                || (bytes.length >= 12
                && "ftyp".equals(new String(bytes, 4, 4, StandardCharsets.US_ASCII)))) {
            return "video/mp4";
        }
        throw new IllegalArgumentException("Media content type could not be determined");
    }

    private static String normalizeContentType(String contentType) {
        int parameterSeparator = contentType.indexOf(';');
        String normalized = parameterSeparator < 0
                ? contentType
                : contentType.substring(0, parameterSeparator);
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasPrefix(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((bytes[index] & 0xFF) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasAsciiPrefix(byte[] bytes, String prefix) {
        byte[] expected = prefix.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (bytes[index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode >= 300 && statusCode < 400;
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
}
