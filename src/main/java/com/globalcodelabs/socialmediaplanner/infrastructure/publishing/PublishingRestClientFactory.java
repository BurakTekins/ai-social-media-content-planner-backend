package com.globalcodelabs.socialmediaplanner.infrastructure.publishing;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;

@Component
public class PublishingRestClientFactory {

    private final RestClient restClient;

    public PublishingRestClientFactory(PublishingProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public RestClient forBaseUrl(String baseUrl) {
        normalizeBaseUrl(baseUrl);
        return restClient;
    }

    public URI endpoint(String baseUrl, String path) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalizeBaseUrl(baseUrl) + normalizedPath);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Social platform base URL cannot be blank");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        URI uri = URI.create(normalized);
        if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException("Social platform base URL must use HTTPS");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalStateException("Social platform base URL cannot contain user information");
        }
        return normalized;
    }
}
