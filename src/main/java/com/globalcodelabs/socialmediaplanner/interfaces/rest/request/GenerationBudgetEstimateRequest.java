package com.globalcodelabs.socialmediaplanner.interfaces.rest.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GenerationBudgetEstimateRequest(
        @Min(1) int requestedCount,
        boolean includeImage,
        boolean includeVideo,
        @Valid @NotNull AiModelSelectionRequest textModel
) {
}
