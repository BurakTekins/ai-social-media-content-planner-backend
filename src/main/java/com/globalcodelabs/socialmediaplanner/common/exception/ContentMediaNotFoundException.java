package com.globalcodelabs.socialmediaplanner.common.exception;

import com.globalcodelabs.socialmediaplanner.domain.model.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ContentMediaNotFoundException extends RuntimeException {

    public ContentMediaNotFoundException(UUID contentId, MediaType mediaType) {
        super("Content media not found: contentId=%s mediaType=%s".formatted(contentId, mediaType));
    }
}
