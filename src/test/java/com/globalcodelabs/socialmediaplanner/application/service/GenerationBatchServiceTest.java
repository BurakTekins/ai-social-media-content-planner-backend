package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.application.command.CreateGenerationBatchCommand;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.LocalDocumentStorage;
import com.globalcodelabs.socialmediaplanner.application.service.GenerationBudgetPolicy;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.common.exception.GenerationBatchNotFoundException;
import com.globalcodelabs.socialmediaplanner.common.exception.GenerationBatchRetryConflictException;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.AiModelSelection;
import com.globalcodelabs.socialmediaplanner.domain.enums.ContentType;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationAttemptStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationAttempt;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationBatchStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationAttemptRepository;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationBatchRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiProviderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationBatchServiceTest {

    @Mock
    private GenerationBatchRepository generationBatchRepository;

    @Mock
    private GenerationAttemptRepository generationAttemptRepository;

    @Mock
    private LocalDocumentStorage documentStorage;

    @Mock
    private GenerationBudgetPolicy generationBudgetPolicy;

    @Mock
    private AiProviderFactory aiProviderFactory;

    private GenerationBatchService generationBatchService;

    @BeforeEach
    void setUp() {
        generationBatchService = new GenerationBatchService(
                generationBatchRepository,
                generationAttemptRepository,
                documentStorage,
                aiProviderFactory,
                generationBudgetPolicy
        );
    }

    @Test
    void atomicallyClaimsFailedBatchForRetry() {
        GenerationBatch batch = failedBatch();
        batch.retry();
        when(generationBatchRepository.claimFailedForRetry(
                eq(batch.id()),
                eq(GenerationBatchStatus.FAILED),
                eq(GenerationBatchStatus.IN_PROGRESS),
                any(OffsetDateTime.class)
        )).thenReturn(1);
        when(generationBatchRepository.findOneById(batch.id())).thenReturn(Optional.of(batch));

        GenerationBatch result = generationBatchService.retry(batch.id());

        assertThat(result.status()).isEqualTo(GenerationBatchStatus.IN_PROGRESS);
        assertThat(result.retryCount()).isEqualTo(1);
        verify(generationBatchRepository).claimFailedForRetry(
                eq(batch.id()),
                eq(GenerationBatchStatus.FAILED),
                eq(GenerationBatchStatus.IN_PROGRESS),
                any(OffsetDateTime.class)
        );
        verify(generationAttemptRepository).markStartedUnknownForBatch(
                eq(batch.id()),
                eq(GenerationAttemptStatus.STARTED),
                eq(GenerationAttemptStatus.UNKNOWN),
                any(String.class),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void rejectsRetryWhenAtomicClaimLosesConcurrencyRace() {
        GenerationBatch batch = createBatch();
        when(generationBatchRepository.claimFailedForRetry(
                eq(batch.id()),
                eq(GenerationBatchStatus.FAILED),
                eq(GenerationBatchStatus.IN_PROGRESS),
                any(OffsetDateTime.class)
        )).thenReturn(0);
        when(generationBatchRepository.findOneById(batch.id())).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> generationBatchService.retry(batch.id()))
                .isInstanceOf(GenerationBatchRetryConflictException.class)
                .hasMessageContaining("current status: IN_PROGRESS");
    }

    @Test
    void reportsMissingBatchWhenRetryTargetDoesNotExist() {
        UUID batchId = UUID.randomUUID();
        when(generationBatchRepository.claimFailedForRetry(
                eq(batchId),
                eq(GenerationBatchStatus.FAILED),
                eq(GenerationBatchStatus.IN_PROGRESS),
                any(OffsetDateTime.class)
        )).thenReturn(0);
        when(generationBatchRepository.findOneById(batchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> generationBatchService.retry(batchId))
                .isInstanceOf(GenerationBatchNotFoundException.class);
    }

    @Test
    void returnsBatchToFailedWhenAsyncDispatchIsRejected() {
        GenerationBatch batch = createBatch();
        when(generationBatchRepository.findByIdForUpdate(batch.id())).thenReturn(Optional.of(batch));
        when(generationBatchRepository.saveAndFlush(batch)).thenReturn(batch);

        generationBatchService.markDispatchFailed(batch.id());

        assertThat(batch.status()).isEqualTo(GenerationBatchStatus.FAILED);
        assertThat(batch.lastError()).isEqualTo("Generation job could not be queued");
    }

    @Test
    void approvesOnlyTheSelectedVideoAttemptWhenTwoSlotsAwaitConsent() {
        GenerationBatch batch = failedBatch();
        GenerationAttempt selectedAttempt = awaitingVideoConsent(batch.id(), 1);
        GenerationAttempt untouchedAttempt = awaitingVideoConsent(batch.id(), 2);

        when(generationAttemptRepository.findById(selectedAttempt.id()))
                .thenReturn(Optional.of(selectedAttempt));
        when(generationAttemptRepository.saveAndFlush(selectedAttempt)).thenReturn(selectedAttempt);
        when(generationBatchRepository.claimFailedForRetry(
                eq(batch.id()),
                eq(GenerationBatchStatus.FAILED),
                eq(GenerationBatchStatus.IN_PROGRESS),
                any(OffsetDateTime.class)
        )).thenAnswer(invocation -> {
            batch.retry();
            return 1;
        });
        when(generationBatchRepository.findOneById(batch.id())).thenReturn(Optional.of(batch));

        GenerationBatchService.VideoRegenerationApproval approval =
                generationBatchService.approveVideoRegeneration(batch.id(), selectedAttempt.id());

        assertThat(approval.generationIndex()).isEqualTo(1);
        assertThat(selectedAttempt.status()).isEqualTo(GenerationAttemptStatus.REGENERATION_APPROVED);
        assertThat(untouchedAttempt.status())
                .isEqualTo(GenerationAttemptStatus.AWAITING_REGENERATION_CONSENT);
        verify(generationBudgetPolicy).validateSingleGeneration(
                AiCapability.VIDEO,
                "gemini",
                "veo-3.1-generate-preview",
                null
        );
        verify(generationAttemptRepository).saveAndFlush(selectedAttempt);
    }

    @Test
    void rejectsVideoRegenerationConsentBeforeApprovalWhenSingleCallExceedsBudget() {
        GenerationBatch batch = failedBatch();
        GenerationAttempt attempt = awaitingVideoConsent(batch.id(), 1);
        when(generationAttemptRepository.findById(attempt.id())).thenReturn(Optional.of(attempt));
        when(generationBatchRepository.findOneById(batch.id())).thenReturn(Optional.of(batch));
        doThrow(new DomainException("Estimated generation cost exceeds configured budget limit"))
                .when(generationBudgetPolicy)
                .validateSingleGeneration(
                        AiCapability.VIDEO,
                        "gemini",
                        "veo-3.1-generate-preview",
                        null
                );

        assertThatThrownBy(() -> generationBatchService.approveVideoRegeneration(
                batch.id(), attempt.id()
        )).isInstanceOf(DomainException.class)
                .hasMessageContaining("budget limit");

        assertThat(attempt.status())
                .isEqualTo(GenerationAttemptStatus.AWAITING_REGENERATION_CONSENT);
        verify(generationAttemptRepository, never()).saveAndFlush(attempt);
        verify(generationBatchRepository, never()).claimFailedForRetry(
                any(), any(), any(), any()
        );
    }

    @Test
    void rejectsUnsupportedVideoProviderBeforePersistingOrStoringDocuments() {
        AiProviderFactory realModeProviderFactory = mock(AiProviderFactory.class);
        when(realModeProviderFactory.supports("openai", AiCapability.TEXT)).thenReturn(true);
        when(realModeProviderFactory.supports("qwen", AiCapability.VIDEO)).thenReturn(false);
        GenerationBatchService realModeService = new GenerationBatchService(
                generationBatchRepository,
                generationAttemptRepository,
                documentStorage,
                realModeProviderFactory,
                generationBudgetPolicy
        );
        CreateGenerationBatchCommand command = new CreateGenerationBatchCommand(
                Platform.INSTAGRAM,
                ContentType.REEL,
                "Video batch",
                1,
                false,
                true,
                "openai",
                "text-model",
                null,
                null,
                "qwen",
                "video-model",
                null,
                null,
                List.of("https://example.com/source"),
                List.of()
        );

        assertThatThrownBy(() -> realModeService.create(command))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("VIDEO");

        verifyNoInteractions(
                generationBatchRepository,
                generationAttemptRepository,
                documentStorage
        );
    }

    @Test
    void rejectsMixedLinkedInMediaBeforeUsingDependencies() {
        CreateGenerationBatchCommand command = new CreateGenerationBatchCommand(
                Platform.LINKEDIN,
                ContentType.POST,
                "LinkedIn mixed media batch",
                1,
                true,
                true,
                "openai",
                "text-model",
                "gemini",
                "image-model",
                "qwen",
                "video-model",
                null,
                null,
                List.of("https://example.com/source"),
                List.of()
        );

        assertThatThrownBy(() -> generationBatchService.create(command))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("LinkedIn")
                .hasMessageContaining("choose only one media type");

        verifyNoInteractions(
                generationBatchRepository,
                generationAttemptRepository,
                documentStorage,
                aiProviderFactory,
                generationBudgetPolicy
        );
    }

    private static GenerationBatch failedBatch() {
        GenerationBatch batch = createBatch();
        batch.markFailed("provider unavailable");
        return batch;
    }

    private static GenerationAttempt awaitingVideoConsent(UUID batchId, int generationIndex) {
        OffsetDateTime submittedAt = OffsetDateTime.now();
        GenerationAttempt attempt = GenerationAttempt.start(
                batchId,
                generationIndex,
                AiCapability.VIDEO,
                "gemini",
                "veo-3.1-generate-preview",
                Integer.toHexString(generationIndex).repeat(64).substring(0, 64)
        );
        attempt.markSubmitted("task-" + generationIndex, null, submittedAt);
        attempt.markProcessing();
        attempt.markProviderSucceeded("task-" + generationIndex, null, submittedAt.plusDays(2));
        attempt.awaitRegenerationConsent("Artifact expired");
        return attempt;
    }

    private static GenerationBatch createBatch() {
        return GenerationBatch.create(
                "Test batch",
                Platform.LINKEDIN,
                ContentType.POST,
                2,
                AiModelSelection.required("openai", "text-model", "Text"),
                null,
                null,
                null,
                null,
                1
        );
    }
}
