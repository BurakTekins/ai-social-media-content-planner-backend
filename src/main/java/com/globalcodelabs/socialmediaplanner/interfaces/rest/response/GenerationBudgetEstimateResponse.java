package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.application.service.GenerationBudgetPolicy;

import java.math.BigDecimal;

public record GenerationBudgetEstimateResponse(
        BigDecimal totalCostUsd,
        BigDecimal textInputCostUsd,
        BigDecimal textOutputCostUsd,
        BigDecimal imageCostUsd,
        BigDecimal videoCostUsd,
        int estimatedInputTokens,
        int estimatedOutputTokens,
        BigDecimal textInputCostUsdPerMillionTokens,
        BigDecimal textOutputCostUsdPerMillionTokens,
        String textPricingSource
) {
    public static GenerationBudgetEstimateResponse from(GenerationBudgetPolicy.Estimate estimate) {
        return new GenerationBudgetEstimateResponse(
                estimate.totalCostUsd(),
                estimate.textInputCostUsd(),
                estimate.textOutputCostUsd(),
                estimate.imageCostUsd(),
                estimate.videoCostUsd(),
                estimate.estimatedInputTokens(),
                estimate.estimatedOutputTokens(),
                estimate.textInputCostUsdPerMillionTokens(),
                estimate.textOutputCostUsdPerMillionTokens(),
                estimate.textPricingSource()
        );
    }
}
