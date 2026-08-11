package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.model.Content;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ContentResponse(
        UUID id,
        String title,
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
        UUID publishOperationId,
        OffsetDateTime publishingStartedAt,
        OffsetDateTime publicationCheckedAt,
        OffsetDateTime publishedAt,
        String failureCode,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ContentResponse from(Content content) {
        UserFacingError failure = UserFacingError.publishing(
                content.platform(), content.status(), content.failureReason()
        );
        return new ContentResponse(
                content.id(), content.title(), content.platform(), content.contentType(), content.status(), content.text(),
                content.hashtags(), content.textProvider(), content.textModel(),
                content.media().stream()
                        .map(media -> ContentMediaResponse.from(content.id(), media))
                        .toList(),
                content.batchId(),
                content.scheduledAt(),
                content.publishOperationId(), content.publishingStartedAt(),
                content.publicationCheckedAt(),
                content.publishedAt(),
                failure == null ? null : failure.code(),
                failure == null ? null : failure.message(),
                content.createdAt(), content.updatedAt()
        );
    }
}
