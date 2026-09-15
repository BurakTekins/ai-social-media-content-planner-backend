package com.globalcodelabs.socialmediaplanner.infrastructure.scheduler;

import com.globalcodelabs.socialmediaplanner.domain.model.GenerationAttempt;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationAttemptRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationResult;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.LocalMediaStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class GenerationAttemptLifecycle {

    private final GenerationAttemptRepository generationAttemptRepository;
    private final LocalMediaStorage mediaStorage;

    void fail(GenerationAttempt attempt, RuntimeException failure) {
        try {
            attempt.fail(
                    GenerationAttemptSupport.errorMessage(failure),
                    GenerationAttemptSupport.submissionUnknown(failure),
                    GenerationAttemptSupport.providerResponseId(failure),
                    GenerationAttemptSupport.providerRequestId(failure)
            );
            generationAttemptRepository.saveAndFlush(attempt);
        } catch (RuntimeException persistenceFailure) {
            log.error("Could not persist failed AI generation attempt attemptId={}",
                    attempt.id(), persistenceFailure);
        }
    }

    void failAfterKnownProviderResult(
            GenerationAttempt attempt,
            AiGenerationResult result,
            RuntimeException failure
    ) {
        try {
            attempt.fail(
                    GenerationAttemptSupport.errorMessage(failure),
                    false,
                    result.providerResponseId(),
                    result.providerRequestId()
            );
            generationAttemptRepository.saveAndFlush(attempt);
        } catch (RuntimeException persistenceFailure) {
            log.error("Could not persist failed generated media attempt attemptId={}",
                    attempt.id(), persistenceFailure);
        }
    }

    void invalidate(GenerationAttempt attempt, RuntimeException failure) {
        try {
            attempt.invalidate(GenerationAttemptSupport.errorMessage(failure));
            generationAttemptRepository.saveAndFlush(attempt);
        } catch (RuntimeException persistenceFailure) {
            log.error("Could not invalidate AI generation attempt attemptId={}",
                    attempt.id(), persistenceFailure);
        }
    }

    void invalidateMediaAfterRetry(
            GenerationBatch batch,
            GenerationAttempt attempt,
            RuntimeException failure
    ) {
        if (batch.retryCount() > 0) {
            invalidate(attempt, failure);
        }
    }

    void deleteStoredMedia(String storageKey) {
        try {
            mediaStorage.delete(storageKey);
        } catch (RuntimeException exception) {
            log.warn("Could not delete uncommitted generated media storageKey={}", storageKey, exception);
        }
    }
}
