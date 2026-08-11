package com.globalcodelabs.socialmediaplanner.infrastructure.ai.video;

public class VideoArtifactExpiredException extends RuntimeException {

    public VideoArtifactExpiredException(String message) {
        super(message);
    }

    public VideoArtifactExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
