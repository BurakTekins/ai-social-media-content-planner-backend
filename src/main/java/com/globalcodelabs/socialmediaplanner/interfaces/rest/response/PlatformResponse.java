package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.model.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;

import java.util.List;

public record PlatformResponse(
        Platform platform,
        List<ContentType> contentTypes
) {
    public static PlatformResponse from(Platform platform) {
        return new PlatformResponse(platform, platform.supportedContentTypes());
    }
}
