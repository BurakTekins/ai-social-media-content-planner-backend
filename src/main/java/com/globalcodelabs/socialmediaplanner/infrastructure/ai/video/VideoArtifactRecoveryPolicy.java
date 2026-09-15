package com.globalcodelabs.socialmediaplanner.infrastructure.ai.video;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class VideoArtifactRecoveryPolicy {

    private final VideoGenerationProperties.Recovery properties;

    public VideoArtifactRecoveryPolicy(VideoGenerationProperties properties) {
        this.properties = properties.getRecovery();
        requirePositive(this.properties.getScanInterval(), "scan interval");
        requirePositive(this.properties.getInitialBackoff(), "initial backoff");
        requirePositive(this.properties.getMaximumBackoff(), "maximum backoff");
        if (this.properties.getBatchSize() <= 0) {
            throw new IllegalStateException("Video artifact recovery batch size must be positive");
        }
    }

    public OffsetDateTime nextRetryAt(int completedRetryCount) {
        double factor = Math.pow(properties.getMultiplier(), Math.min(completedRetryCount, 30));
        long initialMillis = properties.getInitialBackoff().toMillis();
        long maximumMillis = properties.getMaximumBackoff().toMillis();
        long delayMillis = (long) Math.min(maximumMillis, initialMillis * factor);
        return OffsetDateTime.now(ZoneOffset.UTC).plus(Duration.ofMillis(delayMillis));
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
