package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationBatchStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationStrategy;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record GenerationBatchResponse(
        UUID id,
        String title,
        Platform platform,
        ContentType contentType,
        int requestedCount,
        int completedCount,
        GenerationBatchStatus status,
        int retryCount,
        String lastErrorCode,
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
        Integer videoDurationSeconds,
        GenerationStrategy generationStrategy,
        String strategySelectionReason,
        String strategyWarning,
        List<ContentSourceResponse> sources,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static GenerationBatchResponse from(GenerationBatch batch) {
        UserFacingError error = UserFacingError.generation(batch.lastError());
        return new GenerationBatchResponse(
                batch.id(), batch.title(), batch.platform(), batch.contentType(),
                batch.requestedCount(), batch.completedCount(),
                batch.status(), batch.retryCount(),
                error == null ? null : error.code(),
                error == null ? null : error.message(),
                batch.lastRetryAt(),
                batch.includeImage(), batch.includeVideo(), batch.textProvider(), batch.textModel(),
                batch.imageProvider(), batch.imageModel(), batch.videoProvider(), batch.videoModel(),
                batch.videoDurationSeconds(),
                batch.generationStrategy(), batch.strategySelectionReason(), batch.strategyWarning(),
                batch.sources().stream().map(ContentSourceResponse::from).toList(),
                batch.createdAt(), batch.updatedAt()
        );
    }
}
