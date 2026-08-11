package com.globalcodelabs.socialmediaplanner.interfaces.rest.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateGeneralSettingsRequest(
        @NotNull @Min(60) @Max(86400) Long publicationConfirmationTimeoutSeconds,
        @NotNull @Min(5) @Max(300) Long publicationConfirmationIntervalSeconds,
        @NotNull @Min(1) @Max(100) Integer publishingMaxItemsPerRun,
        @NotNull @Min(1) @Max(1000) Integer generationMaxContentsPerBatch,
        @NotNull @Min(0) @Max(1000) Integer generationMaxImagesPerBatch,
        @NotNull @Min(0) @Max(1000) Integer generationMaxVideosPerBatch,
        @NotNull @DecimalMin("0.01") @DecimalMax("10000.00") BigDecimal generationMaxEstimatedCostUsd,
        @NotNull @Min(1) @Max(1000000) Integer generationEstimatedInputTokensPerItem,
        @NotNull @Min(1) @Max(1000000) Integer generationEstimatedOutputTokensPerItem
) {
    @AssertTrue(message = "Publication confirmation interval must be shorter than timeout")
    public boolean isConfirmationIntervalValid() {
        return publicationConfirmationTimeoutSeconds == null
                || publicationConfirmationIntervalSeconds == null
                || publicationConfirmationIntervalSeconds < publicationConfirmationTimeoutSeconds;
    }
}
