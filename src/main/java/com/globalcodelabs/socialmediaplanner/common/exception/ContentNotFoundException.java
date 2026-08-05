package com.globalcodelabs.socialmediaplanner.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ContentNotFoundException extends RuntimeException {

    public ContentNotFoundException(UUID contentId) {
        super("Content not found: " + contentId);
    }
}
