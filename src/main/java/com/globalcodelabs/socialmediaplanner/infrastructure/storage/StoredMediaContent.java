package com.globalcodelabs.socialmediaplanner.infrastructure.storage;

public record StoredMediaContent(String contentType, byte[] bytes) {

    public StoredMediaContent {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Media content type cannot be blank");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Media content cannot be empty");
        }
        contentType = contentType.trim();
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
