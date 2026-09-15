package com.globalcodelabs.socialmediaplanner.infrastructure.ai;

import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;

public record AiGenerationResult(
        AiCapability capability,
        String provider,
        String model,
        String providerResponseId,
        String providerRequestId,
        String output,
        GeneratedMedia generatedMedia
) {
    public AiGenerationResult(
            AiCapability capability,
            String provider,
            String model,
            String providerResponseId,
            String providerRequestId,
            String output
    ) {
        this(capability, provider, model, providerResponseId, providerRequestId, output, null);
    }

    public record GeneratedMedia(String contentType, byte[] bytes) {
        public GeneratedMedia {
            if (contentType == null || contentType.isBlank()) {
                throw new IllegalArgumentException("Generated media content type cannot be blank");
            }
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("Generated media content cannot be empty");
            }
        }
    }
}
