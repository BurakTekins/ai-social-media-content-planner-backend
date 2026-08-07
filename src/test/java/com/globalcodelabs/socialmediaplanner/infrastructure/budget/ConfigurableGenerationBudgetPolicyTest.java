package com.globalcodelabs.socialmediaplanner.infrastructure.budget;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurableGenerationBudgetPolicyTest {

    @Test
    void rejectsRequestedVideoCountAboveConfiguredLimit() {
        GenerationBudgetProperties properties = properties();
        properties.setMaxVideosPerBatch(3);
        ConfigurableGenerationBudgetPolicy policy = new ConfigurableGenerationBudgetPolicy(properties);

        assertThatThrownBy(() -> policy.validate(4, false, true))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("video count")
                .hasMessageContaining("3");
    }

    @Test
    void rejectsEstimatedCostAboveConfiguredUsdBudget() {
        GenerationBudgetProperties properties = properties();
        properties.setMaxEstimatedCostUsd(new BigDecimal("2.00"));
        ConfigurableGenerationBudgetPolicy policy = new ConfigurableGenerationBudgetPolicy(properties);

        assertThatThrownBy(() -> policy.validate(2, true, true))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("2.2200 USD")
                .hasMessageContaining("2.00 USD");
    }

    @Test
    void exposesConfiguredLimitsForClients() {
        GenerationBudgetProperties properties = properties();
        ConfigurableGenerationBudgetPolicy policy = new ConfigurableGenerationBudgetPolicy(properties);

        var limits = policy.limits();

        assertThat(limits.maxContentsPerBatch()).isEqualTo(50);
        assertThat(limits.maxImagesPerBatch()).isEqualTo(50);
        assertThat(limits.maxVideosPerBatch()).isEqualTo(10);
        assertThat(limits.maxEstimatedCostUsd()).isEqualByComparingTo("15.00");
        assertThat(limits.estimatedVideoCostUsdPerItem()).isEqualByComparingTo("1.00");
    }

    private static GenerationBudgetProperties properties() {
        GenerationBudgetProperties properties = new GenerationBudgetProperties();
        properties.setMaxContentsPerBatch(50);
        properties.setMaxImagesPerBatch(50);
        properties.setMaxVideosPerBatch(10);
        properties.setMaxEstimatedCostUsd(new BigDecimal("15.00"));

        GenerationBudgetProperties.EstimatedCostUsd costs = new GenerationBudgetProperties.EstimatedCostUsd();
        costs.setTextPerItem(new BigDecimal("0.01"));
        costs.setImagePerItem(new BigDecimal("0.10"));
        costs.setVideoPerItem(new BigDecimal("1.00"));
        properties.setEstimatedCostUsd(costs);
        return properties;
    }
}
