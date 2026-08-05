package com.globalcodelabs.socialmediaplanner.interfaces.rest.request;

import com.globalcodelabs.socialmediaplanner.domain.model.CredentialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record CreateApiCredentialRequest(
        @NotNull CredentialType credentialType,
        @NotBlank @Size(max = 255) String providerName,
        @Pattern(regexp = ".*\\S.*", message = "Account identifier must not be blank")
        String accountIdentifier,
        @NotBlank String accessToken,
        @Pattern(regexp = ".*\\S.*", message = "Refresh token must not be blank") String refreshToken,
        OffsetDateTime expiresAt
) {
}
