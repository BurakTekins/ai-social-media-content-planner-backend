package com.globalcodelabs.socialmediaplanner.infrastructure.scheduler;

import com.globalcodelabs.socialmediaplanner.application.service.PublishingService;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledPublishingJob {

    private static final String JOB_NAME = "scheduled-publishing";

    private final PublishingService publishingService;
    private final PublishingProperties properties;

    @Scheduled(fixedDelayString = "${publishing.job.fixed-delay:PT30S}")
    public void publishDueContent() {
        long startedAt = System.nanoTime();
        int processedCount = 0;
        MdcUtil.putJobName(JOB_NAME);
        MdcUtil.putCorrelationId("job-%s-%s".formatted(JOB_NAME, UUID.randomUUID()));
        log.info("Scheduled publishing job started");

        try {
            int maxItemsPerRun = properties.getJob().getMaxItemsPerRun();
            while (processedCount < maxItemsPerRun
                    && publishingService.publishNextDueContent()) {
                processedCount++;
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Scheduled publishing job failed processedCount={}",
                    processedCount,
                    exception
            );
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info(
                    "Scheduled publishing job finished processedCount={} durationMs={}",
                    processedCount,
                    durationMs
            );
            MdcUtil.clear();
        }
    }
}
