package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.model.ContentSource;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentSourceStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentSourceType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ContentSourceResponse(
        UUID id,
        ContentSourceType sourceType,
        String sourceValue,
        ContentSourceStatus status,
        String errorMessage,
        OffsetDateTime createdAt
) {
    public static ContentSourceResponse from(ContentSource source) {
        return new ContentSourceResponse(
                source.id(), source.sourceType(), source.sourceValue(), source.status(),
                source.errorMessage(), source.createdAt()
        );
    }
}
