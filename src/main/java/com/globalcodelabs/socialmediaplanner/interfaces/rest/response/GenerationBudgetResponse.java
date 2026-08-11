package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.application.service.GenerationBudgetPolicy;

import java.math.BigDecimal;

public record GenerationBudgetResponse(
        int maxContentsPerBatch,
        int maxImagesPerBatch,
        int maxVideosPerBatch,
        BigDecimal maxEstimatedCostUsd,
        int estimatedInputTokensPerItem,
        int estimatedOutputTokensPerItem,
        int estimatedMediaInputTokensPerItem,
        int estimatedImageOutputTokensPerItem,
        BigDecimal fallbackTextInputCostUsdPerMillionTokens,
        BigDecimal fallbackTextOutputCostUsdPerMillionTokens,
        BigDecimal estimatedImageCostUsdPerItem,
        BigDecimal estimatedVideoCostUsdPerItem
) {
    public static GenerationBudgetResponse from(GenerationBudgetPolicy.Limits limits) {
        return new GenerationBudgetResponse(
                limits.maxContentsPerBatch(),
                limits.maxImagesPerBatch(),
                limits.maxVideosPerBatch(),
                limits.maxEstimatedCostUsd(),
                limits.estimatedInputTokensPerItem(),
                limits.estimatedOutputTokensPerItem(),
                limits.estimatedMediaInputTokensPerItem(),
                limits.estimatedImageOutputTokensPerItem(),
                limits.fallbackTextInputCostUsdPerMillionTokens(),
                limits.fallbackTextOutputCostUsdPerMillionTokens(),
                limits.estimatedImageCostUsdPerItem(),
                limits.estimatedVideoCostUsdPerItem()
        );
    }
}
