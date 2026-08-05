package com.globalcodelabs.socialmediaplanner.application.command;

public record UploadedMedia(
        String originalFilename,
        String contentType,
        byte[] content
) {
    public UploadedMedia {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Media filename cannot be blank");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Media file cannot be empty");
        }
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
