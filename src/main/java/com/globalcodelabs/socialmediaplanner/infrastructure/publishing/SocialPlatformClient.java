package com.globalcodelabs.socialmediaplanner.infrastructure.publishing;

import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;

public interface SocialPlatformClient {

    boolean supports(Platform platform);

    String publish(PublishContentRequest request);

    boolean isPublished(String externalPostId, PlatformCredential credential);
}
