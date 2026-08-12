package com.globalcodelabs.socialmediaplanner.infrastructure.publishing;

import com.globalcodelabs.socialmediaplanner.infrastructure.IntegrationMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
@ConfigurationProperties(prefix = "publishing")
public class PublishingProperties {

    @NotNull
    private IntegrationMode mode = IntegrationMode.MOCK;

    @Valid
    @NotNull
    private Job job = new Job();

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(5);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(60);

    @Min(1)
    private int maxMediaBytes = 536_870_912;

    @Min(0)
    private int maxMediaRedirects = 5;

    @Valid
    private Map<String, Provider> providers = new LinkedHashMap<>();

    public boolean mockModeEnabled() {
        return mode == IntegrationMode.MOCK;
    }

    public Provider requireProvider(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("Social platform provider cannot be blank");
        }
        Provider provider = providers.get(providerName.trim().toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported social platform provider: " + providerName);
        }
        return provider;
    }

    public String requireBaseUrl(String providerName) {
        String baseUrl = requireProvider(providerName).getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Base URL is not configured for social platform provider: " + providerName
            );
        }
        return baseUrl.trim();
    }

    public String requireVersion(String providerName) {
        String version = requireProvider(providerName).getVersion();
        if (version == null || version.isBlank()) {
            throw new IllegalStateException(
                    "API version is not configured for social platform provider: " + providerName
            );
        }
        return version.trim();
    }

    @Getter
    @Setter
    public static class Job {

        @NotNull
        private Duration fixedDelay = Duration.ofSeconds(30);

        @NotNull
        private Duration confirmationInterval = Duration.ofSeconds(30);

        @NotNull
        private Duration confirmationTimeout = Duration.ofMinutes(15);

        @Min(1)
        private int maxItemsPerRun = 20;
    }

    @Getter
    @Setter
    public static class Provider {

        private String baseUrl;

        private String version;

        @NotNull
        private Duration pollInterval = Duration.ofSeconds(2);

        @NotNull
        private Duration pollTimeout = Duration.ofMinutes(2);

        @Min(1)
        private int uploadChunkSizeBytes = 4 * 1024 * 1024;
    }
}
