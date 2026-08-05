package com.globalcodelabs.socialmediaplanner.interfaces.rest.request;

import jakarta.validation.constraints.Pattern;

public record UpdateApiCredentialAccountIdentifierRequest(
        @Pattern(regexp = ".*\\S.*", message = "Account identifier must not be blank")
        String accountIdentifier
) {
}
