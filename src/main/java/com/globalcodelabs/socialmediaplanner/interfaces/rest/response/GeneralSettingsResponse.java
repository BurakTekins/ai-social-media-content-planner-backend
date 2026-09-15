package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.application.service.GeneralSettings;

import java.math.BigDecimal;

public record GeneralSettingsResponse(
        long publicationConfirmationTimeoutSeconds,
        long publicationConfirmationIntervalSeconds,
        int publishingMaxItemsPerRun,
        int generationMaxContentsPerBatch,
        int generationMaxImagesPerBatch,
        int generationMaxVideosPerBatch,
        BigDecimal generationMaxEstimatedCostUsd,
        int generationEstimatedInputTokensPerItem,
        int generationEstimatedOutputTokensPerItem
) {
    public static GeneralSettingsResponse from(GeneralSettings settings) {
        return new GeneralSettingsResponse(
                settings.publicationConfirmationTimeout().toSeconds(),
                settings.publicationConfirmationInterval().toSeconds(),
                settings.publishingMaxItemsPerRun(),
                settings.generationMaxContentsPerBatch(),
                settings.generationMaxImagesPerBatch(),
                settings.generationMaxVideosPerBatch(),
                settings.generationMaxEstimatedCostUsd(),
                settings.generationEstimatedInputTokensPerItem(),
                settings.generationEstimatedOutputTokensPerItem()
        );
    }
}
