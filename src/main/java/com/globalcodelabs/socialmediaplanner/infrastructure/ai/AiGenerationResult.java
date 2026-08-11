package com.globalcodelabs.socialmediaplanner.infrastructure.ai;

import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;

public record AiGenerationResult(
        AiCapability capability,
        String provider,
        String model,
        String providerResponseId,
        String providerRequestId,
        String output
) {
}
