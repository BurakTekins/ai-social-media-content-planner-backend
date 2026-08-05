package com.globalcodelabs.socialmediaplanner.application.command;

public record UploadedDocument(
        String originalFilename,
        byte[] content
) {
}
