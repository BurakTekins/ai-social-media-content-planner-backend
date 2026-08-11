package com.globalcodelabs.socialmediaplanner.infrastructure.scheduler;

import com.globalcodelabs.socialmediaplanner.application.service.GenerationBatchService;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import com.globalcodelabs.socialmediaplanner.domain.enums.GenerationAttemptStatus;
import com.globalcodelabs.socialmediaplanner.domain.repository.GenerationAttemptRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.VideoArtifactRecoveryPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "generation.video.recovery",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class VideoArtifactRecoveryJob {

    private static final String JOB_NAME = "video-artifact-recovery";

    private final GenerationAttemptRepository generationAttemptRepository;
    private final GenerationBatchService generationBatchService;
    private final GenerationBatchJob generationBatchJob;
    private final VideoArtifactRecoveryPolicy recoveryPolicy;

    @Scheduled(fixedDelayString = "${generation.video.recovery.scan-interval:30s}")
    public void recoverDueDownloads() {
        MdcUtil.putCorrelationId("job-" + JOB_NAME + "-" + UUID.randomUUID());
        MdcUtil.putJobName(JOB_NAME);
        try {
            var batchIds = generationAttemptRepository.findDueDownloadRecoveryBatchIds(
                    GenerationAttemptStatus.DOWNLOAD_FAILED,
                    OffsetDateTime.now(),
                    PageRequest.of(0, recoveryPolicy.batchSize())
            );
            if (batchIds.isEmpty()) {
                log.debug("No due video artifact downloads found");
                return;
            }
            for (UUID batchId : batchIds) {
                generationBatchService.retryDueVideoArtifactDownload(batchId)
                        .ifPresent(batch -> generationBatchJob.start(batch.id()));
            }
            log.info("Due video artifact recovery scan completed batchCount={}", batchIds.size());
        } catch (RuntimeException exception) {
            log.error("Video artifact recovery scan failed", exception);
        } finally {
            MdcUtil.clear();
        }
    }
}
