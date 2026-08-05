package com.globalcodelabs.socialmediaplanner.infrastructure.publishing.media;

public record MediaContent(byte[] bytes, String contentType) {
    public MediaContent {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Media content cannot be empty");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Media content type cannot be blank");
        }
        bytes = bytes.clone();
        contentType = contentType.trim().toLowerCase();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
