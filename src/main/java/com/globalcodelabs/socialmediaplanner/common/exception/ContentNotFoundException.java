package com.globalcodelabs.socialmediaplanner.common.exception;

import java.util.UUID;

public class ContentNotFoundException extends ApplicationException {

    public ContentNotFoundException(UUID contentId) {
        super(ErrorCode.CONTENT_NOT_FOUND, "Content not found: " + contentId);
    }
}
