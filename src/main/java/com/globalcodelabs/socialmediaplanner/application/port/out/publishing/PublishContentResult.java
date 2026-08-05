package com.globalcodelabs.socialmediaplanner.application.port.out.publishing;

public record PublishContentResult(String externalPostId) {
    public PublishContentResult {
        if (externalPostId == null || externalPostId.isBlank()) {
            throw new IllegalArgumentException("External post id cannot be blank");
        }
        externalPostId = externalPostId.trim();
    }
}
