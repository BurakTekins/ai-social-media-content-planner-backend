package com.globalcodelabs.socialmediaplanner.infrastructure.ai.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    @NotBlank
    @Pattern(regexp = "mock|real")
    private String mode;

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
    }
}
