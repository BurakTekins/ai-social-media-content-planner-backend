package com.globalcodelabs.socialmediaplanner.infrastructure.scheduler;

import com.globalcodelabs.socialmediaplanner.application.service.AiModelService;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.infrastructure.aimodel.ModelData;
import com.globalcodelabs.socialmediaplanner.infrastructure.aimodel.ModelsDevClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModelSyncJob implements ApplicationRunner {

    private static final String JOB_NAME = "ai-model-sync";

    private final ModelsDevClient modelsDevClient;
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
            var models = modelsDevClient.fetchAll();
            logPricingCoverage(models);
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

    private void logPricingCoverage(List<ModelData> models) {
        Map<AiCapability, PricingCoverage> coverage = new EnumMap<>(AiCapability.class);
        for (AiCapability capability : AiCapability.values()) {
            coverage.put(capability, new PricingCoverage());
        }
        for (ModelData model : models) {
            var cost = model.rawMetadata().path("cost");
            boolean priced = cost.path("input").isNumber()
                    && cost.path("output").isNumber()
                    && cost.path("input").decimalValue().signum() >= 0
                    && cost.path("output").decimalValue().signum() >= 0;
            coverage.get(model.capability()).record(priced);
        }
        log.info(
                "models.dev pricing coverage textPriced={} textFallback={} imagePriced={} "
                        + "imageFallback={} videoPriced={} videoFallback={}",
                coverage.get(AiCapability.TEXT).priced,
                coverage.get(AiCapability.TEXT).fallback,
                coverage.get(AiCapability.IMAGE).priced,
                coverage.get(AiCapability.IMAGE).fallback,
                coverage.get(AiCapability.VIDEO).priced,
                coverage.get(AiCapability.VIDEO).fallback
        );
    }

    private static final class PricingCoverage {
        private int priced;
        private int fallback;

        private void record(boolean hasPricing) {
            if (hasPricing) {
                priced++;
            } else {
                fallback++;
            }
        }
    }
}
