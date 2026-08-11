package com.globalcodelabs.socialmediaplanner.application.service;

import java.math.BigDecimal;
import java.time.Duration;

public record GeneralSettings(
        Duration publicationConfirmationTimeout,
        Duration publicationConfirmationInterval,
        int publishingMaxItemsPerRun,
        int generationMaxContentsPerBatch,
        int generationMaxImagesPerBatch,
        int generationMaxVideosPerBatch,
        BigDecimal generationMaxEstimatedCostUsd,
        int generationEstimatedInputTokensPerItem,
        int generationEstimatedOutputTokensPerItem
) {
}
