package com.globalcodelabs.socialmediaplanner.infrastructure.aimodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;

public record ModelData(
        String providerName,
        String modelId,
        String displayName,
        AiCapability capability,
        JsonNode rawMetadata
) {
}
