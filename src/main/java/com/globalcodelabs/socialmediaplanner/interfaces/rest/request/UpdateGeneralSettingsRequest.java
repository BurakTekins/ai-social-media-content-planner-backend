package com.globalcodelabs.socialmediaplanner.interfaces.rest.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;

public record UpdateGeneralSettingsRequest(
        @NotNull @Min(60) @Max(86400) Long publicationConfirmationTimeoutSeconds,
        @NotNull @Min(5) @Max(300) Long publicationConfirmationIntervalSeconds,
        @NotNull @Min(1) @Max(100) Integer publishingMaxItemsPerRun
) {
    @AssertTrue(message = "Publication confirmation interval must be shorter than timeout")
    public boolean isConfirmationIntervalValid() {
        return publicationConfirmationTimeoutSeconds == null
                || publicationConfirmationIntervalSeconds == null
                || publicationConfirmationIntervalSeconds < publicationConfirmationTimeoutSeconds;
    }
}
