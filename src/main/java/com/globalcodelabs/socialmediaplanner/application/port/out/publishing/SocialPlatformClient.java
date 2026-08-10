package com.globalcodelabs.socialmediaplanner.application.port.out.publishing;

import com.globalcodelabs.socialmediaplanner.domain.model.Platform;

public interface SocialPlatformClient {

    boolean supports(Platform platform);

    PublishContentResult publish(PublishContentRequest request);

    boolean isPublished(String externalPostId, PlatformCredential credential);
}
