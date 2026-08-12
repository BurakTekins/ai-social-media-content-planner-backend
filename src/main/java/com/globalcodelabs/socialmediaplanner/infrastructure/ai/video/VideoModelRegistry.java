package com.globalcodelabs.socialmediaplanner.infrastructure.ai.video;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class VideoModelRegistry {

    private static final int COST_SCALE = 6;

    private final Map<ModelKey, VideoModelSpec> models;

    public VideoModelRegistry(VideoGenerationProperties properties) {
        Map<ModelKey, VideoModelSpec> configuredModels = new LinkedHashMap<>();
        for (VideoGenerationProperties.Model configured : properties.getModels()) {
            ModelKey key = new ModelKey(
                    normalizeProvider(configured.getProvider()),
                    normalizeModel(configured.getModel())
            );
            if (configured.getSupportedDurationSeconds().stream()
                    .anyMatch(duration -> duration == null || duration <= 0)) {
                throw new IllegalStateException("Video model durations must be positive: " + key);
            }
            List<Integer> durations = configured.getSupportedDurationSeconds().stream()
                    .distinct()
                    .sorted()
                    .toList();
            String currency = configured.getCurrency().trim().toUpperCase(Locale.ROOT);
            BigDecimal currencyToUsd = "USD".equals(currency)
                    ? BigDecimal.ONE
                    : Optional.ofNullable(properties.getCurrencyToUsd().get(currency))
                    .orElseThrow(() -> new IllegalStateException(
                            "USD conversion rate is missing for video pricing currency " + currency
                    ));
            VideoModelSpec previous = configuredModels.put(
                    key,
                    new VideoModelSpec(
                            key.provider(),
                            key.model(),
                            durations,
                            configured.getCostPerSecond(),
                            currency,
                            currencyToUsd,
                            configured.getPricingSource().trim()
                    )
            );
            if (previous != null) {
                throw new IllegalStateException("Duplicate video model registry entry: " + key);
            }
        }
        this.models = Map.copyOf(configuredModels);
    }

    public VideoModelSpec require(String provider, String model) {
        ModelKey key = new ModelKey(normalizeProvider(provider), normalizeModel(model));
        VideoModelSpec spec = models.get(key);
        if (spec == null) {
            throw new DomainException(
                    "Video duration configuration is unavailable for provider %s model %s"
                            .formatted(key.provider(), key.model())
            );
        }
        return spec;
    }

    public Optional<VideoModelSpec> find(String provider, String model) {
        if (provider == null || provider.isBlank() || model == null || model.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(models.get(new ModelKey(
                normalizeProvider(provider), normalizeModel(model)
        )));
    }

    public VideoModelSpec validate(String provider, String model, Integer durationSeconds) {
        VideoModelSpec spec = require(provider, model);
        if (durationSeconds == null) {
            throw new DomainException("Video duration is required when video generation is enabled");
        }
        if (!spec.supportedDurationSeconds().contains(durationSeconds)) {
            throw new DomainException(
                    "%s %s supports %s second videos"
                            .formatted(spec.provider(), spec.model(), formatDurations(spec.supportedDurationSeconds()))
            );
        }
        return spec;
    }

    public BigDecimal estimateCostUsd(
            VideoModelSpec spec,
            int durationSeconds,
            int videoCount
    ) {
        return spec.costPerSecond()
                .multiply(spec.currencyToUsd())
                .multiply(BigDecimal.valueOf(durationSeconds))
                .multiply(BigDecimal.valueOf(videoCount))
                .setScale(COST_SCALE, RoundingMode.HALF_UP);
    }

    private static String formatDurations(List<Integer> durations) {
        if (durations.size() == 1) {
            return durations.getFirst().toString();
        }
        return durations.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new DomainException("Video provider cannot be blank");
        }
        return AiProvider.canonicalize(provider);
    }

    private static String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            throw new DomainException("Video model cannot be blank");
        }
        return model.trim().toLowerCase(Locale.ROOT);
    }

    private record ModelKey(String provider, String model) {
    }

    public record VideoModelSpec(
            String provider,
            String model,
            List<Integer> supportedDurationSeconds,
            BigDecimal costPerSecond,
            String currency,
            BigDecimal currencyToUsd,
            String pricingSource
    ) {
        public BigDecimal costUsdPerSecond() {
            return costPerSecond.multiply(currencyToUsd).setScale(COST_SCALE, RoundingMode.HALF_UP);
        }
    }
}
