package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ContentResponse(
        UUID id,
        Platform platform,
        ContentType contentType,
        ContentStatus status,
        String text,
        List<String> hashtags,
        String textProvider,
        String textModel,
        List<ContentMediaResponse> media,
        UUID batchId,
        OffsetDateTime scheduledAt,
        OffsetDateTime publishedAt,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ContentResponse from(Content content) {
        return new ContentResponse(
                content.id(), content.platform(), content.contentType(), content.status(), content.text(),
                content.hashtags(), content.textProvider(), content.textModel(),
                content.media().stream()
                        .map(media -> ContentMediaResponse.from(content.id(), media))
                        .toList(),
                content.batchId(),
                content.scheduledAt(),
                content.publishedAt(), content.failureReason(), content.createdAt(), content.updatedAt()
        );
    }
}
