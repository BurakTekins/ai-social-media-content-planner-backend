package com.globalcodelabs.socialmediaplanner.infrastructure.publishing;

import com.globalcodelabs.socialmediaplanner.domain.enums.MediaType;

import java.util.Objects;

public record PublishMedia(
        MediaType mediaType,
        String storageKey,
        String publicUrl,
        String modelProvider
) {
    public PublishMedia {
        Objects.requireNonNull(mediaType, "Media type cannot be null");
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Media storage key cannot be blank");
        }
        storageKey = storageKey.trim();
        publicUrl = publicUrl == null ? null : publicUrl.trim();
        modelProvider = modelProvider == null || modelProvider.isBlank()
                ? null
                : modelProvider.trim();
    }
}
