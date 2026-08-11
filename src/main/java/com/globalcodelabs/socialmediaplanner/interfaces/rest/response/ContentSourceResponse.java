package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.model.ContentSource;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentSourceStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentSourceType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ContentSourceResponse(
        UUID id,
        ContentSourceType sourceType,
        String sourceValue,
        ContentSourceStatus status,
        String errorCode,
        String errorMessage,
        OffsetDateTime createdAt
) {
    public static ContentSourceResponse from(ContentSource source) {
        UserFacingError error = UserFacingError.source(source.errorMessage());
        return new ContentSourceResponse(
                source.id(), source.sourceType(), source.sourceValue(), source.status(),
                error == null ? null : error.code(),
                error == null ? null : error.message(),
                source.createdAt()
        );
    }
}
