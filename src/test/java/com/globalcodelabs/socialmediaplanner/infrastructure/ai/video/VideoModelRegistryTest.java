package com.globalcodelabs.socialmediaplanner.infrastructure.ai.video;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoModelRegistryTest {

    @Test
    void validatesDurationAgainstProviderAndModelSpecificConfiguration() {
        VideoModelRegistry registry = registry();

        assertThat(registry.validate("google", "veo-3.1", 6).supportedDurationSeconds())
                .containsExactly(4, 6, 8);
        assertThatThrownBy(() -> registry.validate("gemini", "veo-3.1", 10))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("4, 6, 8 second videos");
    }

    @Test
    void calculatesVideoCostFromSelectedDurationAndItemCount() {
        VideoModelRegistry registry = registry();
        VideoModelRegistry.VideoModelSpec spec = registry.require("gemini", "veo-3.1");

        BigDecimal cost = registry.estimateCostUsd(spec, 8, 5);

        assertThat(cost).isEqualByComparingTo("16.000000");
    }

    @Test
    void rejectsDurationForAConfiguredDifferentProviderModel() {
        VideoModelRegistry registry = registry();

        assertThatThrownBy(() -> registry.validate("qwen", "wan2.7-t2v", 8))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("2, 5, 10, 15 second videos");
    }

    private static VideoModelRegistry registry() {
        VideoGenerationProperties properties = new VideoGenerationProperties();
        properties.setModels(List.of(
                model("gemini", "veo-3.1", List.of(4, 6, 8), "0.40"),
                model("qwen", "wan2.7-t2v", List.of(2, 5, 10, 15), "0.20")
        ));
        return new VideoModelRegistry(properties);
    }

    private static VideoGenerationProperties.Model model(
            String provider,
            String model,
            List<Integer> durations,
            String costPerSecond
    ) {
        VideoGenerationProperties.Model configured = new VideoGenerationProperties.Model();
        configured.setProvider(provider);
        configured.setModel(model);
        configured.setSupportedDurationSeconds(durations);
        configured.setCostPerSecond(new BigDecimal(costPerSecond));
        configured.setCurrency("USD");
        configured.setPricingSource("TEST");
        return configured;
    }
}
