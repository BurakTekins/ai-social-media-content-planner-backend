package com.globalcodelabs.socialmediaplanner.infrastructure.ai;

import com.globalcodelabs.socialmediaplanner.infrastructure.IntegrationMode;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ai-provider")
public class AiProviderProperties {

    @NotNull
    private IntegrationMode mode;

    @NotNull
    private Duration connectTimeout;

    @NotNull
    private Duration readTimeout;

    private Map<String, Provider> providers = new LinkedHashMap<>();

    public Provider requireProvider(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("AI provider cannot be blank");
        }
        Provider provider = providers.get(providerName.trim().toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported AI provider: " + providerName);
        }
        return provider;
    }

    public String requireBaseUrl(String providerName) {
        String baseUrl = requireProvider(providerName).getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Base URL is not configured for AI provider: " + providerName);
        }
        return baseUrl.trim();
    }

    public String requireMediaBaseUrl(String providerName) {
        String mediaBaseUrl = requireProvider(providerName).getMediaBaseUrl();
        if (mediaBaseUrl == null || mediaBaseUrl.isBlank()) {
            throw new IllegalStateException("Media base URL is not configured for AI provider: " + providerName);
        }
        return mediaBaseUrl.trim();
    }

    @Getter
    @Setter
    public static class Provider {

        private String baseUrl;

        private String mediaBaseUrl;

        private String videoBaseUrl;

        private Duration videoPollInterval = Duration.ofSeconds(10);

        private Duration videoPollTimeout = Duration.ofMinutes(10);

        private Duration videoDownloadTimeout = Duration.ofMinutes(3);

        private int maxVideoBytes = 536_870_912;

        private int maxVideoRedirects = 5;
    }

    public String requireVideoBaseUrl(String providerName) {
        String videoBaseUrl = requireProvider(providerName).getVideoBaseUrl();
        if (videoBaseUrl == null || videoBaseUrl.isBlank()) {
            throw new IllegalStateException("Video base URL is not configured for AI provider: " + providerName);
        }
        return videoBaseUrl.trim();
    }
}
