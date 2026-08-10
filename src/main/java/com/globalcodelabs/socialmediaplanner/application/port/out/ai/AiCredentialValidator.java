package com.globalcodelabs.socialmediaplanner.application.port.out.ai;

public interface AiCredentialValidator {

    AiCredentialValidationResult validate(String providerName, String accessToken);
}
