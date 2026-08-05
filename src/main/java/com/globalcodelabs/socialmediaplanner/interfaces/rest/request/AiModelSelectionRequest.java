package com.globalcodelabs.socialmediaplanner.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;

public record AiModelSelectionRequest(
        @NotBlank String provider,
        @NotBlank String model
) {
}
