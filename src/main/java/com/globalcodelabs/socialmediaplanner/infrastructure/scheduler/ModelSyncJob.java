package com.globalcodelabs.socialmediaplanner.infrastructure.scheduler;

import com.globalcodelabs.socialmediaplanner.application.port.out.aimodel.ModelsDevCatalogClient;
import com.globalcodelabs.socialmediaplanner.application.service.AiModelService;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModelSyncJob implements ApplicationRunner {

    private static final String JOB_NAME = "ai-model-sync";

    private final ModelsDevCatalogClient modelsDevCatalogClient;
    private final AiModelService aiModelService;

    @Override
    public void run(ApplicationArguments arguments) {
        synchronize("startup");
    }

    @Scheduled(
            cron = "${models-dev.sync.cron:0 0 3 * * *}",
            zone = "${models-dev.sync.zone:Europe/Istanbul}"
    )
    public void synchronizeDaily() {
        synchronize("scheduled");
    }

    void synchronize(String trigger) {
        long startedAt = System.nanoTime();
        int modelCount = 0;
        boolean successful = false;
        MdcUtil.putJobName(JOB_NAME);
        MdcUtil.putCorrelationId("job-%s-%s".formatted(JOB_NAME, UUID.randomUUID()));
        log.info("AI model sync job started trigger={}", trigger);

        try {
            var models = modelsDevCatalogClient.fetchAll();
            modelCount = aiModelService.synchronize(models, OffsetDateTime.now());
            successful = true;
        } catch (RuntimeException exception) {
            log.error("AI model sync job failed trigger={}", trigger, exception);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info(
                    "AI model sync job finished trigger={} successful={} modelCount={} durationMs={}",
                    trigger,
                    successful,
                    modelCount,
                    durationMs
            );
            MdcUtil.clear();
        }
    }
}
