package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.model.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatchStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationStrategy;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record GenerationBatchResponse(
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
        GenerationStrategy generationStrategy,
        String strategySelectionReason,
        String strategyWarning,
        List<ContentSourceResponse> sources,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static GenerationBatchResponse from(GenerationBatch batch) {
        return new GenerationBatchResponse(
                batch.id(), batch.platform(), batch.contentType(), batch.requestedCount(), batch.completedCount(),
                batch.status(), batch.retryCount(), batch.lastError(), batch.lastRetryAt(),
                batch.includeImage(), batch.includeVideo(), batch.textProvider(), batch.textModel(),
                batch.imageProvider(), batch.imageModel(), batch.videoProvider(), batch.videoModel(),
                batch.generationStrategy(), batch.strategySelectionReason(), batch.strategyWarning(),
                batch.sources().stream().map(ContentSourceResponse::from).toList(),
                batch.createdAt(), batch.updatedAt()
        );
    }
}
