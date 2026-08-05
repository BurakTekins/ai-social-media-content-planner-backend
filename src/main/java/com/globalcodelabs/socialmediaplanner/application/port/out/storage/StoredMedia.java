package com.globalcodelabs.socialmediaplanner.application.port.out.storage;

import com.globalcodelabs.socialmediaplanner.domain.model.MediaType;

import java.util.Objects;

public record StoredMedia(
        String storageKey,
        MediaType mediaType,
        String contentType
) {

    public StoredMedia {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Media storage key cannot be blank");
        }
        storageKey = storageKey.trim();
        mediaType = Objects.requireNonNull(mediaType, "Media type cannot be null");
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Media content type cannot be blank");
        }
        contentType = contentType.trim();
    }
}
