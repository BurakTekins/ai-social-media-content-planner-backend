package com.globalcodelabs.socialmediaplanner.application.service;

import java.math.BigDecimal;

public interface GenerationBudgetPolicy {

    void validate(int requestedCount, boolean includeImage, boolean includeVideo);

    Limits limits();

    record Limits(
            int maxContentsPerBatch,
            int maxImagesPerBatch,
            int maxVideosPerBatch,
            BigDecimal maxEstimatedCostUsd,
            BigDecimal estimatedTextCostUsdPerItem,
            BigDecimal estimatedImageCostUsdPerItem,
            BigDecimal estimatedVideoCostUsdPerItem
    ) {
    }
}
