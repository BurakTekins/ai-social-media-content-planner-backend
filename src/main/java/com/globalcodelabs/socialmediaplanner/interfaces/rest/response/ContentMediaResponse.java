package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.model.ContentMedia;
import com.globalcodelabs.socialmediaplanner.domain.model.MediaType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ContentMediaResponse(
        UUID id,
        MediaType mediaType,
        String storageKey,
        String publicUrl,
        String resourcePath,
        String modelProvider,
        String modelId,
        OffsetDateTime createdAt
) {
    static ContentMediaResponse from(UUID contentId, ContentMedia media) {
        String resourcePath = media.storageKey().startsWith("media/")
                ? "/api/contents/%s/media/%s/file".formatted(contentId, media.mediaType())
                : null;
        return new ContentMediaResponse(
                media.id(), media.mediaType(), media.storageKey(), media.publicUrl(), resourcePath,
                media.modelProvider(), media.modelId(), media.createdAt()
        );
    }
}
