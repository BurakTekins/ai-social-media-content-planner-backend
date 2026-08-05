package com.globalcodelabs.socialmediaplanner.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;

public record RotateApiCredentialRequest(
        @NotBlank String accessToken,
        @Pattern(regexp = ".*\\S.*", message = "Refresh token must not be blank") String refreshToken,
        OffsetDateTime expiresAt
) {
}
