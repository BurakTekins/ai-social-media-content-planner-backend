package com.globalcodelabs.socialmediaplanner.infrastructure.ai;

import com.globalcodelabs.socialmediaplanner.infrastructure.ai.config.AiProviderProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
public class AiRestClientFactory {

    private final AiProviderProperties properties;
    private final ConcurrentMap<String, RestClient> clients = new ConcurrentHashMap<>();

    public RestClient forBaseUrl(String baseUrl) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        return clients.computeIfAbsent(normalizedBaseUrl, this::create);
    }

    public URI endpoint(String baseUrl, String path) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalizeBaseUrl(baseUrl) + normalizedPath);
    }

    private RestClient create(String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("AI provider base URL cannot be blank");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        URI uri = URI.create(normalized);
        if (!uri.isAbsolute()) {
            throw new IllegalStateException("AI provider base URL must be absolute");
        }
        return normalized;
    }
}
