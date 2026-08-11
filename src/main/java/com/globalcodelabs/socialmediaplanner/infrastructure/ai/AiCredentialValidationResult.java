package com.globalcodelabs.socialmediaplanner.infrastructure.ai;

public record AiCredentialValidationResult(boolean valid, String failureReason) {

    public static AiCredentialValidationResult succeeded() {
        return new AiCredentialValidationResult(true, null);
    }

    public static AiCredentialValidationResult rejected(String failureReason) {
        return new AiCredentialValidationResult(false, failureReason);
    }
}
