package com.globalcodelabs.socialmediaplanner.application.service;

import java.math.BigDecimal;

public interface GenerationBudgetPolicy {

    void validate(Request request);

    Estimate estimate(Request request);

    Limits limits();

    record Request(
            int requestedCount,
            boolean includeImage,
            boolean includeVideo,
            String textProvider,
            String textModel
    ) {
    }

    record Estimate(
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
    }

    record Limits(
            int maxContentsPerBatch,
            int maxImagesPerBatch,
            int maxVideosPerBatch,
            BigDecimal maxEstimatedCostUsd,
            int estimatedInputTokensPerItem,
            int estimatedOutputTokensPerItem,
            BigDecimal fallbackTextInputCostUsdPerMillionTokens,
            BigDecimal fallbackTextOutputCostUsdPerMillionTokens,
            BigDecimal estimatedImageCostUsdPerItem,
            BigDecimal estimatedVideoCostUsdPerItem
    ) {
    }
}
