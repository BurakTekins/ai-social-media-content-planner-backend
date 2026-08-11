package com.globalcodelabs.socialmediaplanner.infrastructure.ai.video;

import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationResult;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiProviderProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class AiGeneratedVideoDownloader {

    private static final String USER_AGENT = "ai-social-media-content-planner/1.0";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public AiGenerationResult.GeneratedMedia download(
            String sourceUrl,
            Map<String, String> headers,
            Set<String> allowedHostSuffixes,
            AiProviderProperties.Provider provider
    ) {
        URI currentUri = validateUri(sourceUrl, allowedHostSuffixes);
        for (int redirectCount = 0; redirectCount <= provider.getMaxVideoRedirects(); redirectCount++) {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(currentUri)
                    .timeout(provider.getVideoDownloadTimeout())
                    .header("User-Agent", USER_AGENT)
                    .GET();
            headers.forEach(requestBuilder::header);
            try {
                HttpResponse<InputStream> response = httpClient.send(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofInputStream()
                );
                try (InputStream body = response.body()) {
                    if (isRedirect(response.statusCode())) {
                        if (redirectCount == provider.getMaxVideoRedirects()) {
                            throw new IllegalStateException("Generated video exceeded redirect limit");
                        }
                        String location = response.headers().firstValue("Location")
                                .orElseThrow(() -> new IllegalStateException(
                                        "Generated video redirect is missing Location header"
                                ));
                        currentUri = validateUri(currentUri.resolve(location).toString(), allowedHostSuffixes);
                        continue;
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        if (response.statusCode() == 404 || response.statusCode() == 410) {
                            throw new VideoArtifactExpiredException(
                                    "Generated video artifact is expired or no longer available"
                            );
                        }
                        throw new IllegalStateException(
                                "Generated video download returned HTTP status " + response.statusCode()
                        );
                    }
                    int maximumBytes = requireMaximumBytes(provider.getMaxVideoBytes());
                    long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                    if (contentLength > maximumBytes) {
                        throw new IllegalArgumentException("Generated video exceeds configured size limit");
                    }
                    byte[] bytes = readLimited(
                            body,
                            maximumBytes,
                            provider.getVideoDownloadTimeout()
                    );
                    if (bytes.length > maximumBytes) {
                        throw new IllegalArgumentException("Generated video exceeds configured size limit");
                    }
                    String contentType = response.headers().firstValue("Content-Type")
                            .map(AiGeneratedVideoDownloader::normalizeContentType)
                            .orElse("video/mp4");
                    if ("application/octet-stream".equals(contentType)) {
                        contentType = "video/mp4";
                    }
                    if (!"video/mp4".equals(contentType)) {
                        throw new IllegalArgumentException("Generated video response is not MP4");
                    }
                    return new AiGenerationResult.GeneratedMedia(contentType, bytes);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Could not download generated video", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Generated video download was interrupted", exception);
            }
        }
        throw new IllegalStateException("Generated video exceeded redirect limit");
    }

    private static URI validateUri(String value, Set<String> allowedHostSuffixes) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Generated video URL is invalid");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Generated video URL must be a valid HTTPS URL");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowedHost = allowedHostSuffixes.stream()
                .map(suffix -> suffix.toLowerCase(Locale.ROOT))
                .anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix));
        if (!allowedHost) {
            throw new IllegalArgumentException("Generated video URL host is not allowed");
        }
        try {
            if (Arrays.stream(InetAddress.getAllByName(host)).anyMatch(AiGeneratedVideoDownloader::isPrivateAddress)) {
                throw new IllegalArgumentException("Private network generated video URLs are not allowed");
            }
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Generated video URL host could not be resolved");
        }
        return uri;
    }

    private static boolean isPrivateAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }

    private static String normalizeContentType(String value) {
        int parameterSeparator = value.indexOf(';');
        return (parameterSeparator < 0 ? value : value.substring(0, parameterSeparator))
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static int requireMaximumBytes(int maximumBytes) {
        if (maximumBytes <= 0 || maximumBytes == Integer.MAX_VALUE) {
            throw new IllegalStateException("AI provider max video bytes must be between 1 and 2147483646");
        }
        return maximumBytes;
    }

    private static byte[] readLimited(InputStream body, int maximumBytes, Duration timeout)
            throws IOException, InterruptedException {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException("AI provider video download timeout must be positive");
        }
        FutureTask<byte[]> readTask = new FutureTask<>(() -> body.readNBytes(maximumBytes + 1));
        Thread reader = Thread.ofVirtual()
                .name("generated-video-download")
                .start(readTask);
        try {
            return readTask.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            readTask.cancel(true);
            throw new IOException("Generated video download timed out", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Could not read generated video", cause);
        } finally {
            if (reader.isAlive()) {
                reader.interrupt();
            }
        }
    }
}
