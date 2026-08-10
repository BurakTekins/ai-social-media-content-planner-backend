package com.globalcodelabs.socialmediaplanner.interfaces.rest.request;

import com.globalcodelabs.socialmediaplanner.domain.model.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationStrategy;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateGenerationBatchRequest(
        @NotNull Platform platform,
        @NotNull ContentType contentType,
        @Min(1) int requestedCount,
        boolean includeImage,
        boolean includeVideo,
        @NotNull @Valid AiModelSelectionRequest textModel,
        @Valid AiModelSelectionRequest imageModel,
        @Valid AiModelSelectionRequest videoModel,
        GenerationStrategy generationStrategy,
        List<@NotBlank String> links
) {
}
