package com.globalcodelabs.socialmediaplanner.interfaces.rest.request;

import com.globalcodelabs.socialmediaplanner.domain.enums.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateContentMediaRequest(
        @NotNull MediaType mediaType,
        @NotBlank String storageKey,
        String publicUrl,
        String modelProvider,
        String modelId
) {
}
