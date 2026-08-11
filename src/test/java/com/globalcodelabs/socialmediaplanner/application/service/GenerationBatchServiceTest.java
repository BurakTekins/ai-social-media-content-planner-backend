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
                1
        );
    }
}
