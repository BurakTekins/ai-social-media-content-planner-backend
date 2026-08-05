package com.globalcodelabs.socialmediaplanner.domain.model;

import java.util.List;

public enum Platform {
    LINKEDIN(List.of(ContentType.POST)),
    INSTAGRAM(List.of(ContentType.POST, ContentType.REEL)),
    TWITTER(List.of(ContentType.TWEET));

    private final List<ContentType> supportedContentTypes;

    Platform(List<ContentType> supportedContentTypes) {
        this.supportedContentTypes = supportedContentTypes;
    }

    public List<ContentType> supportedContentTypes() {
        return supportedContentTypes;
    }

    public boolean supports(ContentType contentType) {
        return supportedContentTypes.contains(contentType);
    }
}
