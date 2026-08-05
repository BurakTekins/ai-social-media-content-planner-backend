package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.model.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatchStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GenerationBatchSummaryResponse(
        UUID id,
        Platform platform,
        ContentType contentType,
        int requestedCount,
        int completedCount,
        GenerationBatchStatus status,
        int retryCount,
        String lastError,
        OffsetDateTime lastRetryAt,
        boolean includeImage,
        boolean includeVideo,
        String textProvider,
        String textModel,
        String imageProvider,
        String imageModel,
        String videoProvider,
        String videoModel,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static GenerationBatchSummaryResponse from(GenerationBatch batch) {
        return new GenerationBatchSummaryResponse(
                batch.id(),
                batch.platform(),
                batch.contentType(),
                batch.requestedCount(),
                batch.completedCount(),
                batch.status(),
                batch.retryCount(),
                batch.lastError(),
                batch.lastRetryAt(),
                batch.includeImage(),
                batch.includeVideo(),
                batch.textProvider(),
                batch.textModel(),
                batch.imageProvider(),
                batch.imageModel(),
                batch.videoProvider(),
                batch.videoModel(),
                batch.createdAt(),
                batch.updatedAt()
        );
    }
}
