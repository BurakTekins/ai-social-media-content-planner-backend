package com.globalcodelabs.socialmediaplanner.infrastructure.publishing.mock;

import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.PublishContentRequest;
import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.PublishContentResult;
import com.globalcodelabs.socialmediaplanner.application.port.out.publishing.SocialPlatformClient;
import com.globalcodelabs.socialmediaplanner.domain.model.Platform;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class MockSocialPlatformClient implements SocialPlatformClient {

    @Override
    public boolean supports(Platform platform) {
        return platform != null;
    }

    @Override
    public PublishContentResult publish(PublishContentRequest request) {
        String externalPostId = "mock-%s-%s".formatted(
                request.platform().name().toLowerCase(Locale.ROOT),
                request.contentId()
        );
        return new PublishContentResult(externalPostId);
    }
}
