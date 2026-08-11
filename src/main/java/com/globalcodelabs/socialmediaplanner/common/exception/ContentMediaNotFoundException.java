package com.globalcodelabs.socialmediaplanner.common.exception;

import com.globalcodelabs.socialmediaplanner.domain.enums.MediaType;

import java.util.UUID;

public class ContentMediaNotFoundException extends ApplicationException {

    public ContentMediaNotFoundException(UUID contentId, MediaType mediaType) {
        super(
                ErrorCode.CONTENT_MEDIA_NOT_FOUND,
                "Content media not found: contentId=%s mediaType=%s".formatted(contentId, mediaType)
        );
    }
}
