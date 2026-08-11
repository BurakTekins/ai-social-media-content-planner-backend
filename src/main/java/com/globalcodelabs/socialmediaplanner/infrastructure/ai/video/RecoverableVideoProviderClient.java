package com.globalcodelabs.socialmediaplanner.infrastructure.ai.video;

import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationResult;

import java.time.OffsetDateTime;
import java.util.Objects;

public interface RecoverableVideoProviderClient {

    VideoTaskSubmission submitVideo(AiGenerationRequest request);

    VideoArtifactReference awaitVideo(VideoTaskSubmission submission);

    VideoArtifactReference resolveCompletedVideo(
            String taskId,
            String providerRequestId,
            OffsetDateTime submittedAt
    );

    AiGenerationResult.GeneratedMedia downloadVideo(VideoArtifactReference artifact);

    record VideoTaskSubmission(
            String taskId,
            String providerRequestId,
            OffsetDateTime submittedAt
    ) {
        public VideoTaskSubmission {
            if (taskId == null || taskId.isBlank()) {
                throw new IllegalArgumentException("Provider video task id cannot be blank");
            }
            taskId = taskId.trim();
            providerRequestId = normalize(providerRequestId);
            submittedAt = Objects.requireNonNull(submittedAt, "Provider submission time cannot be null");
        }
    }

    record VideoArtifactReference(
            String taskId,
            String providerRequestId,
            String artifactUrl,
            OffsetDateTime expiresAt
    ) {
        public VideoArtifactReference {
            if (taskId == null || taskId.isBlank()) {
                throw new IllegalArgumentException("Provider video task id cannot be blank");
            }
            if (artifactUrl == null || artifactUrl.isBlank()) {
                throw new IllegalArgumentException("Provider video artifact URL cannot be blank");
            }
            taskId = taskId.trim();
            providerRequestId = normalize(providerRequestId);
            artifactUrl = artifactUrl.trim();
            expiresAt = Objects.requireNonNull(expiresAt, "Provider video artifact expiry cannot be null");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
