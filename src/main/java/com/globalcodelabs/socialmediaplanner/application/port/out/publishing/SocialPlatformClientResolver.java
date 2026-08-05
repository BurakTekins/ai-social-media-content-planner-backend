package com.globalcodelabs.socialmediaplanner.application.port.out.publishing;

import com.globalcodelabs.socialmediaplanner.domain.model.Platform;

public interface SocialPlatformClientResolver {

    SocialPlatformClient resolve(Platform platform);
}
