package com.globalcodelabs.socialmediaplanner.infrastructure.aimodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class ModelsDevClient {

    private static final Map<String, String> PROVIDER_MAPPING = providerMapping();

    private final RestClient restClient;
    private final URI apiUri;
    private final int maxAttempts;
    private final Duration retryDelay;

    @Autowired
    public ModelsDevClient(ModelsDevProperties properties) {
        URI configuredApiUri = requireHttpUri(properties.getApiUrl());
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        RestClient configuredRestClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.restClient = configuredRestClient;
        this.apiUri = configuredApiUri;
        this.maxAttempts = requirePositive(properties.getMaxAttempts());
        this.retryDelay = requireNonNegative(properties.getRetryDelay());
    }

    ModelsDevClient(RestClient restClient, URI apiUri, int maxAttempts, Duration retryDelay) {
        this.restClient = restClient;
        this.apiUri = apiUri;
        this.maxAttempts = requirePositive(maxAttempts);
        this.retryDelay = requireNonNegative(retryDelay);
    }

    public List<ModelData> fetchAll() {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                JsonNode response = restClient.get()
                        .uri(apiUri)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(JsonNode.class);
                return parseCatalog(response);
            } catch (RestClientException exception) {
                if (attempt == maxAttempts) {
                    throw exception;
                }
                log.warn(
                        "models.dev request failed; retrying attempt={} maxAttempts={} delayMs={}",
                        attempt,
                        maxAttempts,
                        retryDelay.toMillis(),
                        exception
                );
                waitBeforeRetry();
            }
        }
        throw new IllegalStateException("models.dev request attempts exhausted");
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(retryDelay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("models.dev retry interrupted", exception);
        }
    }

    static List<ModelData> parseCatalog(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("models.dev response root must be a JSON object");
        }

        List<ModelData> models = new ArrayList<>();
        PROVIDER_MAPPING.forEach((sourceProvider, providerName) ->
                parseProvider(root, sourceProvider, providerName, models)
        );
        if (models.isEmpty()) {
            throw new IllegalStateException("models.dev response contains no supported models");
        }
        return List.copyOf(models);
    }

    private static void parseProvider(
            JsonNode root,
            String sourceProvider,
            String providerName,
            List<ModelData> target
    ) {
        JsonNode providerNode = requireObject(
                root.get(sourceProvider),
                "models.dev provider is missing or invalid: " + sourceProvider
        );
        JsonNode modelsNode = requireObject(
                providerNode.get("models"),
                "models.dev models map is missing or invalid for provider: " + sourceProvider
        );
        if (modelsNode.isEmpty()) {
            throw new IllegalStateException(
                    "models.dev models map is empty for provider: " + sourceProvider
            );
        }

        int acceptedBefore = target.size();
        modelsNode.properties().forEach(entry -> {
            JsonNode modelNode = requireObject(
                    entry.getValue(),
                    "models.dev model metadata is invalid: " + sourceProvider + "/" + entry.getKey()
            );
            String modelId = requireText(
                    modelNode.get("id"),
                    "models.dev model id is missing: " + sourceProvider + "/" + entry.getKey()
            );
            String displayName = requireText(
                    modelNode.get("name"),
                    "models.dev model name is missing: " + sourceProvider + "/" + modelId
            );
            Set<AiCapability> capabilities = resolveCapabilities(
                    modelNode,
                    sourceProvider,
                    modelId
            );
            if (capabilities.isEmpty()) {
                log.warn(
                        "models.dev model skipped because output capability is unsupported "
                                + "sourceProvider={} modelId={}",
                        sourceProvider,
                        modelId
                );
                return;
            }
            capabilities.forEach(capability -> target.add(
                    new ModelData(
                            providerName,
                            modelId,
                            displayName,
                            capability,
                            modelNode.deepCopy()
                    )
            ));
        });

        if (target.size() == acceptedBefore) {
            throw new IllegalStateException(
                    "models.dev provider contains no supported models: " + sourceProvider
            );
        }
    }

    private static Set<AiCapability> resolveCapabilities(
            JsonNode modelNode,
            String providerName,
            String modelId
    ) {
        JsonNode modalities = requireObject(
                modelNode.get("modalities"),
                "models.dev modalities are missing: " + providerName + "/" + modelId
        );
        JsonNode output = modalities.get("output");
        if (output == null || !output.isArray()) {
            throw new IllegalStateException(
                    "models.dev output modalities are missing: " + providerName + "/" + modelId
            );
        }

        Set<AiCapability> capabilities = EnumSet.noneOf(AiCapability.class);
        output.forEach(modality -> {
            if (!modality.isTextual() || modality.textValue().isBlank()) {
                throw new IllegalStateException(
                        "models.dev output modality is invalid: " + providerName + "/" + modelId
                );
            }
            switch (modality.textValue().toLowerCase(Locale.ROOT)) {
                case "text" -> capabilities.add(AiCapability.TEXT);
                case "image" -> capabilities.add(AiCapability.IMAGE);
                case "video" -> capabilities.add(AiCapability.VIDEO);
                default -> {
                }
            }
        });
        return capabilities;
    }

    private static JsonNode requireObject(JsonNode node, String message) {
        if (node == null || !node.isObject()) {
            throw new IllegalStateException(message);
        }
        return node;
    }

    private static String requireText(JsonNode node, String message) {
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalStateException(message);
        }
        return node.textValue().trim();
    }

    private static URI requireHttpUri(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("models.dev API URL cannot be blank");
        }
        URI uri = URI.create(value.trim());
        if (!uri.isAbsolute()
                || (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalStateException("models.dev API URL must be an absolute HTTP(S) URL");
        }
        return uri;
    }

    private static int requirePositive(int value) {
        if (value < 1) {
            throw new IllegalStateException("models.dev max attempts must be at least 1");
        }
        return value;
    }

    private static Duration requireNonNegative(Duration value) {
        if (value == null || value.isNegative()) {
            throw new IllegalStateException("models.dev retry delay cannot be negative");
        }
        return value;
    }

    private static Map<String, String> providerMapping() {
        Map<String, String> providers = new LinkedHashMap<>();
        providers.put("openai", "openai");
        providers.put("anthropic", "anthropic");
        providers.put("google", "gemini");
        providers.put("deepseek", "deepseek");
        providers.put("alibaba", "qwen");
        return Collections.unmodifiableMap(providers);
    }
}
