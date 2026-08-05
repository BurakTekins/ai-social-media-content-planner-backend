package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.model.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.AiModelCache;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiModelResponse(
        UUID id,
        String providerName,
        String modelId,
        String displayName,
        AiCapability capability,
        OffsetDateTime lastSyncedAt
) {
    public static AiModelResponse from(AiModelCache model) {
        return new AiModelResponse(
                model.id(),
                model.providerName(),
                model.modelId(),
                model.displayName(),
                model.capability(),
                model.lastSyncedAt()
        );
    }
}
