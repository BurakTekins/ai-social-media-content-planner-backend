package com.globalcodelabs.socialmediaplanner.interfaces.rest.request;

import jakarta.validation.constraints.NotNull;

public record UpdateApiCredentialActiveRequest(
        @NotNull Boolean active
) {
}
