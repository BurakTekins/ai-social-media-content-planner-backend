package com.globalcodelabs.socialmediaplanner.infrastructure.scheduler;

import com.globalcodelabs.socialmediaplanner.application.service.GenerationBatchService;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationAttemptStatus;
import com.globalcodelabs.socialmediaplanner.domain.model.GenerationBatch;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationAttemptRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.VideoArtifactRecoveryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoArtifactRecoveryJobTest {

    @Mock
    private GenerationAttemptRepository generationAttemptRepository;

    @Mock
    private GenerationBatchService generationBatchService;

    @Mock
    private GenerationBatchJob generationBatchJob;

    @Mock
    private VideoArtifactRecoveryPolicy recoveryPolicy;

    @Test
    void dispatchesOnlyBatchesWhoseDownloadRetryIsDue() {
        UUID dueBatchId = UUID.randomUUID();
        UUID alreadyClaimedBatchId = UUID.randomUUID();
        GenerationBatch dueBatch = mock(GenerationBatch.class);
        when(dueBatch.id()).thenReturn(dueBatchId);
        when(recoveryPolicy.batchSize()).thenReturn(20);
        when(generationAttemptRepository.findDueDownloadRecoveryBatchIds(
                eq(GenerationAttemptStatus.DOWNLOAD_FAILED),
                any(OffsetDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(dueBatchId, alreadyClaimedBatchId));
        when(generationBatchService.retryDueVideoArtifactDownload(dueBatchId))
                .thenReturn(Optional.of(dueBatch));
        when(generationBatchService.retryDueVideoArtifactDownload(alreadyClaimedBatchId))
                .thenReturn(Optional.empty());

        recoveryJob().recoverDueDownloads();

        verify(generationBatchJob).start(dueBatchId);
        verify(generationBatchJob, never()).start(alreadyClaimedBatchId);
    }

    @Test
    void doesNotDispatchAnythingWhenNoDownloadRetryIsDue() {
        when(recoveryPolicy.batchSize()).thenReturn(20);
        when(generationAttemptRepository.findDueDownloadRecoveryBatchIds(
                eq(GenerationAttemptStatus.DOWNLOAD_FAILED),
                any(OffsetDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of());

        recoveryJob().recoverDueDownloads();

        verifyNoInteractions(generationBatchService, generationBatchJob);
    }

    private VideoArtifactRecoveryJob recoveryJob() {
        return new VideoArtifactRecoveryJob(
                generationAttemptRepository,
                generationBatchService,
                generationBatchJob,
                recoveryPolicy
        );
    }
}
