package com.globalcodelabs.socialmediaplanner.interfaces.rest.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RegenerateMediaRequest(
        @NotBlank String provider,
        @NotBlank String model,
        @Min(1) Integer videoDurationSeconds
) {
}
