package com.globalcodelabs.socialmediaplanner.application.port.out.ai;

import com.globalcodelabs.socialmediaplanner.domain.model.AiCapability;

public record AiGenerationResult(
        AiCapability capability,
        String provider,
        String model,
        String providerResponseId,
        String providerRequestId,
        String output
) {
}
