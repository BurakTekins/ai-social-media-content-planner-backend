package com.globalcodelabs.socialmediaplanner.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.AiModelCache;
import com.globalcodelabs.socialmediaplanner.domain.repository.AiModelCacheRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.budget.GenerationBudgetProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationBudgetPolicyTest {

    @Mock
    private AiModelCacheRepository aiModelCacheRepository;

    @Test
    void rejectsRequestedVideoCountAboveConfiguredLimit() {
        GenerationBudgetProperties properties = properties();
        properties.setMaxVideosPerBatch(3);
        GenerationBudgetPolicy policy = policy(properties);

        assertThatThrownBy(() -> policy.validate(request(4, false, true)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("video count")
                .hasMessageContaining("3");
    }

    @Test
    void rejectsEstimatedCostAboveConfiguredUsdBudgetUsingFallbackPricing() {
        GenerationBudgetProperties properties = properties();
        properties.setMaxEstimatedCostUsd(new BigDecimal("2.00"));
        when(aiModelCacheRepository.findByProviderNameAndModelIdAndCapability(
                "openai", "missing-model", AiCapability.TEXT
        )).thenReturn(Optional.empty());
        GenerationBudgetPolicy policy = policy(properties);

        assertThatThrownBy(() -> policy.validate(request(2, true, true)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("2.221000 USD")
                .hasMessageContaining("2.00 USD");
    }

    @Test
    void calculatesTextInputAndOutputSeparatelyUsingModelsDevPricing() throws Exception {
        GenerationBudgetProperties properties = properties();
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
        GenerationBudgetPolicy policy = policy(properties);

        GenerationBudgetPolicy.Estimate estimate = policy.estimate(new GenerationBudgetPolicy.Request(
                10,
                false,
                false,
                "openai",
                "priced-model"
        ));

        assertThat(estimate.estimatedInputTokens()).isEqualTo(20_000);
        assertThat(estimate.estimatedOutputTokens()).isEqualTo(3_000);
        assertThat(estimate.textInputCostUsd()).isEqualByComparingTo("0.020000");
        assertThat(estimate.textOutputCostUsd()).isEqualByComparingTo("0.015000");
        assertThat(estimate.totalCostUsd()).isEqualByComparingTo("0.035000");
        assertThat(estimate.textPricingSource()).isEqualTo("MODELS_DEV");
    }

    @Test
    void exposesConfiguredLimitsForClients() {
        GenerationBudgetProperties properties = properties();
        GenerationBudgetPolicy policy = policy(properties);

        var limits = policy.limits();

        assertThat(limits.maxContentsPerBatch()).isEqualTo(50);
        assertThat(limits.maxImagesPerBatch()).isEqualTo(50);
        assertThat(limits.maxVideosPerBatch()).isEqualTo(10);
        assertThat(limits.maxEstimatedCostUsd()).isEqualByComparingTo("15.00");
        assertThat(limits.estimatedInputTokensPerItem()).isEqualTo(2_000);
        assertThat(limits.estimatedOutputTokensPerItem()).isEqualTo(300);
        assertThat(limits.fallbackTextInputCostUsdPerMillionTokens()).isEqualByComparingTo("3.00");
        assertThat(limits.fallbackTextOutputCostUsdPerMillionTokens()).isEqualByComparingTo("15.00");
        assertThat(limits.estimatedVideoCostUsdPerItem()).isEqualByComparingTo("1.00");
    }

    private GenerationBudgetPolicy policy(GenerationBudgetProperties properties) {
        return new GenerationBudgetPolicy(properties, aiModelCacheRepository);
    }

    private static GenerationBudgetPolicy.Request request(
            int requestedCount,
            boolean includeImage,
            boolean includeVideo
    ) {
        return new GenerationBudgetPolicy.Request(
                requestedCount,
                includeImage,
                includeVideo,
                "openai",
                "missing-model"
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

        GenerationBudgetProperties.EstimatedCostUsd costs = new GenerationBudgetProperties.EstimatedCostUsd();
        costs.setFallbackTextInputPerMillionTokens(new BigDecimal("3.00"));
        costs.setFallbackTextOutputPerMillionTokens(new BigDecimal("15.00"));
        costs.setImagePerItem(new BigDecimal("0.10"));
        costs.setVideoPerItem(new BigDecimal("1.00"));
        properties.setEstimatedCostUsd(costs);
        return properties;
    }
}
