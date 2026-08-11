package com.globalcodelabs.socialmediaplanner.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.AiModelCache;
import com.globalcodelabs.socialmediaplanner.domain.repository.AiModelCacheRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.image.ImageModelPricingRegistry;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.image.ImageGenerationProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.VideoModelRegistry;
import com.globalcodelabs.socialmediaplanner.infrastructure.budget.GenerationBudgetProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationBudgetPolicyTest {

    @Mock
    private AiModelCacheRepository aiModelCacheRepository;

    @Mock
    private GeneralSettingsService generalSettingsService;

    @Mock
    private VideoModelRegistry videoModelRegistry;

    @Test
    void rejectsRequestedVideoCountAboveConfiguredLimit() {
        GenerationBudgetProperties properties = properties();
        GenerationBudgetPolicy policy = policy(properties, new BigDecimal("15.00"), 3);

        assertThatThrownBy(() -> policy.validate(videoRequest(4, 8)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("video count")
                .hasMessageContaining("3");
    }

    @Test
    void evaluatesInitialBatchAsOneCombinedEstimate() {
        GenerationBudgetProperties properties = properties();
        GenerationBudgetPolicy policy = policy(properties, new BigDecimal("15.00"), 10);
        VideoModelRegistry.VideoModelSpec spec = videoSpec(new BigDecimal("0.40"));
        when(aiModelCacheRepository.findByProviderNameAndModelIdAndCapability(
                "openai", "text-model", AiCapability.TEXT
        )).thenReturn(Optional.empty());
        when(videoModelRegistry.validate("gemini", "veo-3.1", 8)).thenReturn(spec);
        when(videoModelRegistry.estimateCostUsd(spec, 8, 5)).thenReturn(new BigDecimal("16.000000"));

        assertThatThrownBy(() -> policy.validate(videoRequest(5, 8)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("exceeds configured budget limit")
                .hasMessageContaining("15.00 USD");
    }

    @Test
    void validatesSingleVideoRegenerationIndependently() {
        GenerationBudgetProperties properties = properties();
        GenerationBudgetPolicy policy = policy(properties, new BigDecimal("3.00"), 10);
        VideoModelRegistry.VideoModelSpec spec = videoSpec(new BigDecimal("0.40"));
        when(videoModelRegistry.validate("gemini", "veo-3.1", 8)).thenReturn(spec);
        when(videoModelRegistry.estimateCostUsd(spec, 8, 1)).thenReturn(new BigDecimal("3.200000"));

        assertThatThrownBy(() -> policy.validateSingleGeneration(
                AiCapability.VIDEO, "gemini", "veo-3.1", 8
        )).isInstanceOf(DomainException.class)
                .hasMessageContaining("3.200000 USD")
                .hasMessageContaining("3.00 USD");
    }

    @Test
    void validatesSingleImageRegenerationUsingFallbackPrice() {
        GenerationBudgetProperties properties = properties();
        GenerationBudgetPolicy policy = policy(properties, new BigDecimal("0.05"), 10);
        when(aiModelCacheRepository.findByProviderNameAndModelIdAndCapability(
                "openai", "image-model", AiCapability.IMAGE
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policy.validateSingleGeneration(
                AiCapability.IMAGE, "openai", "image-model", null
        )).isInstanceOf(DomainException.class)
                .hasMessageContaining("0.100000 USD")
                .hasMessageContaining("0.05 USD");
    }

    @Test
    void validatesSingleTextRegenerationUsingModelsDevPricing() throws Exception {
        GenerationBudgetProperties properties = properties();
        GenerationBudgetPolicy policy = policy(properties, new BigDecimal("0.003"), 10);
        AiModelCache cachedModel = AiModelCache.create(
                "openai",
                "priced-model",
                "Priced model",
                AiCapability.TEXT,
                new ObjectMapper().readTree("{\"cost\":{\"input\":1,\"output\":5}}"),
                OffsetDateTime.now()
        );
        when(aiModelCacheRepository.findByProviderNameAndModelIdAndCapability(
                "openai", "priced-model", AiCapability.TEXT
        )).thenReturn(Optional.of(cachedModel));

        assertThatThrownBy(() -> policy.validateSingleGeneration(
                AiCapability.TEXT, "openai", "priced-model", null
        )).isInstanceOf(DomainException.class)
                .hasMessageContaining("0.003500 USD")
                .hasMessageContaining("0.003 USD");
    }

    @Test
    void exposesConfiguredLimitsForClients() {
        GenerationBudgetProperties properties = properties();
        GenerationBudgetPolicy policy = policy(properties, new BigDecimal("15.00"), 10);

        GenerationBudgetPolicy.Limits limits = policy.limits();

        assertThat(limits.maxContentsPerBatch()).isEqualTo(50);
        assertThat(limits.maxImagesPerBatch()).isEqualTo(50);
        assertThat(limits.maxVideosPerBatch()).isEqualTo(10);
        assertThat(limits.maxEstimatedCostUsd()).isEqualByComparingTo("15.00");
        assertThat(limits.estimatedInputTokensPerItem()).isEqualTo(2_000);
        assertThat(limits.estimatedOutputTokensPerItem()).isEqualTo(300);
        assertThat(limits.estimatedMediaInputTokensPerItem()).isEqualTo(500);
        assertThat(limits.estimatedImageOutputTokensPerItem()).isEqualTo(1_290);
        assertThat(limits.fallbackTextInputCostUsdPerMillionTokens()).isEqualByComparingTo("3.00");
        assertThat(limits.fallbackTextOutputCostUsdPerMillionTokens()).isEqualByComparingTo("15.00");
        assertThat(limits.estimatedImageCostUsdPerItem()).isEqualByComparingTo("0.10");
    }

    private GenerationBudgetPolicy policy(
            GenerationBudgetProperties properties,
            BigDecimal maxEstimatedCostUsd,
            int maxVideosPerBatch
    ) {
        when(generalSettingsService.get()).thenReturn(new GeneralSettings(
                Duration.ofMinutes(15),
                Duration.ofSeconds(30),
                25,
                50,
                50,
                maxVideosPerBatch,
                maxEstimatedCostUsd,
                properties.getEstimatedInputTokensPerItem(),
                properties.getEstimatedOutputTokensPerItem()
        ));
        return new GenerationBudgetPolicy(
                properties,
                aiModelCacheRepository,
                generalSettingsService,
                new ImageModelPricingRegistry(new ImageGenerationProperties()),
                videoModelRegistry
        );
    }

    private static GenerationBudgetPolicy.Request videoRequest(int requestedCount, int durationSeconds) {
        return new GenerationBudgetPolicy.Request(
                requestedCount,
                false,
                true,
                "openai",
                "text-model",
                null,
                null,
                "gemini",
                "veo-3.1",
                durationSeconds
        );
    }

    private static VideoModelRegistry.VideoModelSpec videoSpec(BigDecimal costPerSecond) {
        return new VideoModelRegistry.VideoModelSpec(
                "gemini",
                "veo-3.1",
                List.of(4, 6, 8),
                costPerSecond,
                "USD",
                BigDecimal.ONE,
                "TEST"
        );
    }

    private static GenerationBudgetProperties properties() {
        GenerationBudgetProperties properties = new GenerationBudgetProperties();
        properties.setMaxContentsPerBatch(50);
        properties.setMaxImagesPerBatch(50);
        properties.setMaxVideosPerBatch(10);
        properties.setMaxEstimatedCostUsd(new BigDecimal("15.00"));
        properties.setEstimatedInputTokensPerItem(2_000);
        properties.setEstimatedOutputTokensPerItem(300);
        properties.setEstimatedMediaInputTokensPerItem(500);
        properties.setEstimatedImageOutputTokensPerItem(1_290);

        GenerationBudgetProperties.EstimatedCostUsd costs = new GenerationBudgetProperties.EstimatedCostUsd();
        costs.setFallbackTextInputPerMillionTokens(new BigDecimal("3.00"));
        costs.setFallbackTextOutputPerMillionTokens(new BigDecimal("15.00"));
        costs.setImagePerItem(new BigDecimal("0.10"));
        costs.setVideoPerItem(new BigDecimal("1.00"));
        properties.setEstimatedCostUsd(costs);
        return properties;
    }
}
