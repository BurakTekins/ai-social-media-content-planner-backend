package com.globalcodelabs.socialmediaplanner.interfaces.rest.request;

import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationStrategy;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateGenerationBatchRequest(
        @NotNull Platform platform,
        @NotNull ContentType contentType,
        @NotBlank @Size(max = 240) String title,
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
