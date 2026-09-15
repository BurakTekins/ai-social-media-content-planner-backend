package com.globalcodelabs.socialmediaplanner.infrastructure.scheduler;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationAttemptStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.MediaType;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationAttempt;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationAttemptRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationResult;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiProviderFactory;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.RecoverableVideoProviderClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.VideoArtifactExpiredException;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.VideoArtifactRecoveryPolicy;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.VideoProviderTaskFailedException;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.LocalMediaStorage;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.StoredMedia;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
class RecoverableVideoGeneration {

    private final GenerationAttemptRepository generationAttemptRepository;
    private final AiProviderFactory aiProviderFactory;
    private final LocalMediaStorage mediaStorage;
    private final VideoArtifactRecoveryPolicy recoveryPolicy;
    private final GenerationAttemptLifecycle attemptLifecycle;

    GenerationAttempt generate(
            GenerationAttempt attempt,
            AiGenerationRequest request,
            RecoverableVideoProviderClient providerClient
    ) {
        RecoverableVideoProviderClient.VideoTaskSubmission submission;
        try {
            submission = providerClient.submitVideo(request);
            attempt.markSubmitted(
                    submission.taskId(),
                    submission.providerRequestId(),
                    submission.submittedAt()
            );
            generationAttemptRepository.saveAndFlush(attempt);
            attempt.markProcessing();
            generationAttemptRepository.saveAndFlush(attempt);
        } catch (RuntimeException exception) {
            if (attempt.status() == GenerationAttemptStatus.SUBMITTED) {
                attempt.markProcessing();
                scheduleDownloadRetry(attempt, exception);
            } else if (attempt.status() == GenerationAttemptStatus.PROCESSING) {
                scheduleDownloadRetry(attempt, exception);
            } else {
                attemptLifecycle.fail(attempt, exception);
            }
            throw exception;
        }

        RecoverableVideoProviderClient.VideoArtifactReference artifact;
        try {
            artifact = providerClient.awaitVideo(submission);
            attempt.markProviderSucceeded(
                    artifact.taskId(),
                    artifact.providerRequestId(),
                    artifact.expiresAt()
            );
            generationAttemptRepository.saveAndFlush(attempt);
        } catch (VideoProviderTaskFailedException exception) {
            attemptLifecycle.fail(attempt, exception);
            throw exception;
        } catch (RuntimeException exception) {
            scheduleDownloadRetry(attempt, exception);
            throw exception;
        }
        return downloadAndComplete(attempt, providerClient, artifact);
    }

    GenerationAttempt recover(GenerationAttempt attempt, AiGenerationRequest request) {
        if (attempt.status() == GenerationAttemptStatus.AWAITING_REGENERATION_CONSENT) {
            throw new DomainException("Video regeneration requires explicit user consent");
        }
        if (attempt.status() == GenerationAttemptStatus.DOWNLOADED) {
            attempt.completeDownloadedVideo("Provider video task completed");
            return generationAttemptRepository.saveAndFlush(attempt);
        }
        if (attempt.artifactExpiresAt() != null
                && !OffsetDateTime.now(ZoneOffset.UTC).isBefore(attempt.artifactExpiresAt())) {
            awaitRegenerationConsent(attempt, "Provider video artifact retention window has expired");
            throw new DomainException("Video regeneration requires explicit user consent");
        }

        RecoverableVideoProviderClient providerClient = aiProviderFactory
                .findRecoverableVideo(request.provider())
                .orElseThrow(() -> new DomainException(
                        "Configured provider does not support video artifact recovery"
                ));
        if (attempt.status() == GenerationAttemptStatus.DOWNLOAD_FAILED
                || attempt.status() == GenerationAttemptStatus.SUBMITTED
                || attempt.status() == GenerationAttemptStatus.PROVIDER_SUCCEEDED) {
            attempt.markProcessing();
            generationAttemptRepository.saveAndFlush(attempt);
        }

        RecoverableVideoProviderClient.VideoArtifactReference artifact;
        try {
            artifact = providerClient.resolveCompletedVideo(
                    attempt.providerResponseId(),
                    attempt.providerRequestId(),
                    attempt.providerSubmittedAt()
            );
        } catch (VideoArtifactExpiredException exception) {
            awaitRegenerationConsent(attempt, GenerationAttemptSupport.errorMessage(exception));
            throw exception;
        } catch (VideoProviderTaskFailedException exception) {
            attemptLifecycle.fail(attempt, exception);
            throw exception;
        } catch (RuntimeException exception) {
            scheduleDownloadRetry(attempt, exception);
            throw exception;
        }

        OffsetDateTime expiresAt = attempt.artifactExpiresAt() == null
                ? artifact.expiresAt()
                : attempt.artifactExpiresAt();
        attempt.markProviderSucceeded(
                artifact.taskId(),
                artifact.providerRequestId(),
                expiresAt
        );
        generationAttemptRepository.saveAndFlush(attempt);
        return downloadAndComplete(attempt, providerClient, artifact);
    }

    private GenerationAttempt downloadAndComplete(
            GenerationAttempt attempt,
            RecoverableVideoProviderClient providerClient,
            RecoverableVideoProviderClient.VideoArtifactReference artifact
    ) {
        StoredMedia storedMedia;
        try {
            AiGenerationResult.GeneratedMedia generatedMedia = providerClient.downloadVideo(artifact);
            storedMedia = mediaStorage.store(
                    MediaType.VIDEO,
                    generatedMedia.contentType(),
                    generatedMedia.bytes()
            );
        } catch (VideoArtifactExpiredException exception) {
            awaitRegenerationConsent(attempt, GenerationAttemptSupport.errorMessage(exception));
            throw exception;
        } catch (RuntimeException exception) {
            scheduleDownloadRetry(attempt, exception);
            throw exception;
        }
        try {
            attempt.markDownloaded(storedMedia.storageKey(), storedMedia.contentType());
            generationAttemptRepository.saveAndFlush(attempt);
        } catch (RuntimeException exception) {
            attemptLifecycle.deleteStoredMedia(storedMedia.storageKey());
            throw exception;
        }
        attempt.completeDownloadedVideo("Provider video task completed");
        return generationAttemptRepository.saveAndFlush(attempt);
    }

    private void scheduleDownloadRetry(GenerationAttempt attempt, RuntimeException failure) {
        if (attempt.artifactExpiresAt() != null
                && !OffsetDateTime.now(ZoneOffset.UTC).isBefore(attempt.artifactExpiresAt())) {
            awaitRegenerationConsent(attempt, "Provider video artifact retention window has expired");
            return;
        }
        attempt.markDownloadFailed(
                GenerationAttemptSupport.errorMessage(failure),
                recoveryPolicy.nextRetryAt(attempt.downloadRetryCount())
        );
        generationAttemptRepository.saveAndFlush(attempt);
    }

    private void awaitRegenerationConsent(GenerationAttempt attempt, String reason) {
        attempt.awaitRegenerationConsent(reason);
        generationAttemptRepository.saveAndFlush(attempt);
    }
}
