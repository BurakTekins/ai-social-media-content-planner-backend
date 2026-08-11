package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;

import java.util.List;

public record PlatformResponse(
        Platform platform,
        List<ContentType> contentTypes
) {
    public static PlatformResponse from(Platform platform) {
        return new PlatformResponse(platform, platform.supportedContentTypes());
    }
}
