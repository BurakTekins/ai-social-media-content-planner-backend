package com.globalcodelabs.socialmediaplanner.infrastructure.scheduler;

import com.globalcodelabs.socialmediaplanner.application.service.GenerationBudgetPolicy;
import com.globalcodelabs.socialmediaplanner.common.exception.ConcurrentGenerationException;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationAttemptStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.MediaType;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationAttempt;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationAttemptRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationRequest;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiGenerationResult;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiProviderFactory;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.RecoverableVideoProviderClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.LocalMediaStorage;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.StoredMedia;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
class GenerationAttemptExecutor {

    private static final List<GenerationAttemptStatus> IN_FLIGHT_ATTEMPT_STATUSES = List.of(
            GenerationAttemptStatus.STARTED,
            GenerationAttemptStatus.SUBMITTED,
            GenerationAttemptStatus.PROCESSING,
            GenerationAttemptStatus.PROVIDER_SUCCEEDED,
            GenerationAttemptStatus.DOWNLOAD_FAILED,
            GenerationAttemptStatus.DOWNLOADED,
            GenerationAttemptStatus.AWAITING_REGENERATION_CONSENT
    );
    private static final List<GenerationAttemptStatus> RECOVERABLE_VIDEO_ATTEMPT_STATUSES = List.of(
            GenerationAttemptStatus.SUBMITTED,
            GenerationAttemptStatus.PROCESSING,
            GenerationAttemptStatus.PROVIDER_SUCCEEDED,
            GenerationAttemptStatus.DOWNLOAD_FAILED,
            GenerationAttemptStatus.DOWNLOADED,
            GenerationAttemptStatus.AWAITING_REGENERATION_CONSENT
    );

    private final GenerationAttemptRepository generationAttemptRepository;
    private final GenerationBudgetPolicy generationBudgetPolicy;
    private final AiProviderFactory aiProviderFactory;
    private final LocalMediaStorage mediaStorage;
    private final GenerationAttemptLifecycle attemptLifecycle;
    private final RecoverableVideoGeneration recoverableVideoGeneration;

    GenerationAttempt execute(
            GenerationBatch batch,
            int generationIndex,
            AiGenerationRequest request
    ) {
        String promptHash = GenerationAttemptSupport.promptHash(request.prompt());
        GenerationAttempt reusable = generationAttemptRepository
                .findFirstByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusOrderByCreatedAtDesc(
                        batch.id(), generationIndex, request.capability(), promptHash,
                        GenerationAttemptStatus.SUCCEEDED
                )
                .orElse(null);
        if (reusable != null) {
            return reuseCompletedAttempt(batch, generationIndex, request, reusable);
        }

        if (request.capability() == AiCapability.VIDEO) {
            GenerationAttempt recoverableAttempt = generationAttemptRepository
                    .findFirstByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusInOrderByCreatedAtDesc(
                            batch.id(), generationIndex, request.capability(), promptHash,
                            RECOVERABLE_VIDEO_ATTEMPT_STATUSES
                    )
                    .orElse(null);
            if (recoverableAttempt != null) {
                return recoverableVideoGeneration.recover(recoverableAttempt, request);
            }
        }

        if (batch.retryCount() > 0) {
            generationBudgetPolicy.validateSingleGeneration(
                    request.capability(),
                    request.provider(),
                    request.model(),
                    request.videoDurationSeconds()
            );
        }

        GenerationAttempt attempt = GenerationAttempt.start(
                batch.id(), generationIndex, batch.retryCount(), request.capability(),
                request.provider(), request.model(), promptHash
        );
        try {
            generationAttemptRepository.saveAndFlush(attempt);
        } catch (DataIntegrityViolationException exception) {
            GenerationAttempt completedAttempt = generationAttemptRepository
                    .findFirstByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusOrderByCreatedAtDesc(
                            batch.id(), generationIndex, request.capability(), promptHash,
                            GenerationAttemptStatus.SUCCEEDED
                    )
                    .orElse(null);
            if (completedAttempt != null) {
                return reuseCompletedAttempt(batch, generationIndex, request, completedAttempt);
            }
            boolean activeAttemptExists = generationAttemptRepository
                    .existsByBatchIdAndGenerationIndexAndCapabilityAndPromptHashAndStatusIn(
                            batch.id(), generationIndex, request.capability(), promptHash,
                            IN_FLIGHT_ATTEMPT_STATUSES
                    );
            if (activeAttemptExists) {
                throw new ConcurrentGenerationException();
            }
            throw exception;
        }

        AiGenerationResult result;
        RecoverableVideoProviderClient recoverableVideoProviderClient = request.capability() == AiCapability.VIDEO
                ? aiProviderFactory.findRecoverableVideo(request.provider()).orElse(null)
                : null;
        if (recoverableVideoProviderClient != null) {
            return recoverableVideoGeneration.generate(attempt, request, recoverableVideoProviderClient);
        }
        try {
            result = aiProviderFactory.resolve(request.provider()).generate(request);
        } catch (RuntimeException exception) {
            attemptLifecycle.fail(attempt, exception);
            throw exception;
        }
        StoredMedia generatedMedia = null;
        if (result.generatedMedia() != null) {
            try {
                generatedMedia = mediaStorage.store(
                        request.capability() == AiCapability.IMAGE ? MediaType.IMAGE : MediaType.VIDEO,
                        result.generatedMedia().contentType(),
                        result.generatedMedia().bytes()
                );
            } catch (RuntimeException exception) {
                attemptLifecycle.failAfterKnownProviderResult(attempt, result, exception);
                throw exception;
            }
        }
        attempt.succeed(
                result.capability(),
                result.provider(),
                result.model(),
                result.providerResponseId(),
                result.providerRequestId(),
                result.output()
        );
        if (generatedMedia != null) {
            attempt.recordStoredMedia(generatedMedia.storageKey(), generatedMedia.contentType());
        }
        try {
            return generationAttemptRepository.saveAndFlush(attempt);
        } catch (RuntimeException exception) {
            if (generatedMedia != null) {
                attemptLifecycle.deleteStoredMedia(generatedMedia.storageKey());
            }
            throw exception;
        }
    }

    private GenerationAttempt reuseCompletedAttempt(
            GenerationBatch batch,
            int generationIndex,
            AiGenerationRequest request,
            GenerationAttempt attempt
    ) {
        if (attempt.retryNumber() >= batch.retryCount()) {
            throw new ConcurrentGenerationException();
        }
        log.info("AI generation result reused capability={} model={} generationIndex={}",
                request.capability(), request.model(), generationIndex);
        return attempt;
    }
}
