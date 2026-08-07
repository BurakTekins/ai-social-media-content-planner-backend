package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.application.service.GenerationBudgetPolicy;

import java.math.BigDecimal;

public record GenerationBudgetResponse(
        int maxContentsPerBatch,
        int maxImagesPerBatch,
        int maxVideosPerBatch,
        BigDecimal maxEstimatedCostUsd,
        BigDecimal estimatedTextCostUsdPerItem,
        BigDecimal estimatedImageCostUsdPerItem,
        BigDecimal estimatedVideoCostUsdPerItem
) {
    public static GenerationBudgetResponse from(GenerationBudgetPolicy.Limits limits) {
        return new GenerationBudgetResponse(
                limits.maxContentsPerBatch(),
                limits.maxImagesPerBatch(),
                limits.maxVideosPerBatch(),
                limits.maxEstimatedCostUsd(),
                limits.estimatedTextCostUsdPerItem(),
                limits.estimatedImageCostUsdPerItem(),
                limits.estimatedVideoCostUsdPerItem()
        );
    }
}
