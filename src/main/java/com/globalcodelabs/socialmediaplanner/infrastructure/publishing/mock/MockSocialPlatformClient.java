package com.globalcodelabs.socialmediaplanner.infrastructure.publishing.mock;

import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishContentRequest;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PlatformCredential;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.SocialPlatformClient;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class MockSocialPlatformClient implements SocialPlatformClient {

    @Override
    public boolean supports(Platform platform) {
        return platform != null;
    }

    @Override
    public String publish(PublishContentRequest request) {
        return "mock-%s-%s".formatted(
                request.platform().name().toLowerCase(Locale.ROOT),
                request.contentId()
        );
    }

    @Override
    public boolean isPublished(String externalPostId, PlatformCredential credential) {
        return externalPostId != null && externalPostId.startsWith("mock-");
    }
}
