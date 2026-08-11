package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.AiModelCache;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AiModelResponse(
        UUID id,
        String providerName,
        String modelId,
        String displayName,
        AiCapability capability,
        List<Integer> supportedVideoDurationSeconds,
        OffsetDateTime lastSyncedAt
) {
    public static AiModelResponse from(AiModelCache model, List<Integer> supportedVideoDurationSeconds) {
        return new AiModelResponse(
                model.id(),
                model.providerName(),
                model.modelId(),
                model.displayName(),
                model.capability(),
                supportedVideoDurationSeconds,
                model.lastSyncedAt()
        );
    }
}
