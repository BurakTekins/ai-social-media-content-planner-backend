package com.globalcodelabs.socialmediaplanner.infrastructure.ai.video;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

@Component
public class VideoArtifactRecoveryPolicy {

    private final VideoArtifactRecoveryProperties properties;

    public VideoArtifactRecoveryPolicy(VideoArtifactRecoveryProperties properties) {
        this.properties = properties;
        requirePositive(properties.getScanInterval(), "scan interval");
        requirePositive(properties.getInitialBackoff(), "initial backoff");
        requirePositive(properties.getMaximumBackoff(), "maximum backoff");
        if (properties.getBatchSize() <= 0) {
            throw new IllegalStateException("Video artifact recovery batch size must be positive");
        }
    }

    public OffsetDateTime nextRetryAt(int completedRetryCount) {
        double factor = Math.pow(properties.getMultiplier(), Math.min(completedRetryCount, 30));
        long initialMillis = properties.getInitialBackoff().toMillis();
        long maximumMillis = properties.getMaximumBackoff().toMillis();
        long delayMillis = (long) Math.min(maximumMillis, initialMillis * factor);
        return OffsetDateTime.now().plus(Duration.ofMillis(delayMillis));
    }

    public int batchSize() {
        return properties.getBatchSize();
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException("Video artifact recovery " + name + " must be positive");
        }
    }
}
