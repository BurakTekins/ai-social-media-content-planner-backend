package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.domain.model.ApplicationSetting;
import com.globalcodelabs.socialmediaplanner.domain.repository.ApplicationSettingRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.budget.GenerationBudgetProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeneralSettingsService {

    private static final String CONFIRMATION_TIMEOUT_SECONDS = "publishing.confirmation-timeout-seconds";
    private static final String CONFIRMATION_INTERVAL_SECONDS = "publishing.confirmation-interval-seconds";
    private static final String MAX_ITEMS_PER_RUN = "publishing.max-items-per-run";
    private static final String MAX_CONTENTS_PER_BATCH = "generation.max-contents-per-batch";
    private static final String MAX_IMAGES_PER_BATCH = "generation.max-images-per-batch";
    private static final String MAX_VIDEOS_PER_BATCH = "generation.max-videos-per-batch";
    private static final String MAX_ESTIMATED_COST_USD = "generation.max-estimated-cost-usd";
    private static final String ESTIMATED_INPUT_TOKENS_PER_ITEM = "generation.estimated-input-tokens-per-item";
    private static final String ESTIMATED_OUTPUT_TOKENS_PER_ITEM = "generation.estimated-output-tokens-per-item";

    private final ApplicationSettingRepository repository;
    private final PublishingProperties publishingDefaults;
    private final GenerationBudgetProperties budgetDefaults;

    @Transactional(readOnly = true)
    public GeneralSettings get() {
        Map<String, ApplicationSetting> settings = repository.findAllById(List.of(
                CONFIRMATION_TIMEOUT_SECONDS,
                CONFIRMATION_INTERVAL_SECONDS,
                MAX_ITEMS_PER_RUN,
                MAX_CONTENTS_PER_BATCH,
                MAX_IMAGES_PER_BATCH,
                MAX_VIDEOS_PER_BATCH,
                MAX_ESTIMATED_COST_USD,
                ESTIMATED_INPUT_TOKENS_PER_ITEM,
                ESTIMATED_OUTPUT_TOKENS_PER_ITEM
        )).stream().collect(Collectors.toMap(ApplicationSetting::key, Function.identity()));
        return new GeneralSettings(
                Duration.ofSeconds(readLong(settings, CONFIRMATION_TIMEOUT_SECONDS,
                        publishingDefaults.getJob().getConfirmationTimeout().toSeconds())),
                Duration.ofSeconds(readLong(settings, CONFIRMATION_INTERVAL_SECONDS,
                        publishingDefaults.getJob().getConfirmationInterval().toSeconds())),
                Math.toIntExact(readLong(settings, MAX_ITEMS_PER_RUN,
                        publishingDefaults.getJob().getMaxItemsPerRun())),
                Math.toIntExact(readLong(settings, MAX_CONTENTS_PER_BATCH,
                        budgetDefaults.getMaxContentsPerBatch())),
                Math.toIntExact(readLong(settings, MAX_IMAGES_PER_BATCH,
                        budgetDefaults.getMaxImagesPerBatch())),
                Math.toIntExact(readLong(settings, MAX_VIDEOS_PER_BATCH,
                        budgetDefaults.getMaxVideosPerBatch())),
                readDecimal(settings, MAX_ESTIMATED_COST_USD,
                        budgetDefaults.getMaxEstimatedCostUsd()),
                Math.toIntExact(readLong(settings, ESTIMATED_INPUT_TOKENS_PER_ITEM,
                        budgetDefaults.getEstimatedInputTokensPerItem())),
                Math.toIntExact(readLong(settings, ESTIMATED_OUTPUT_TOKENS_PER_ITEM,
                        budgetDefaults.getEstimatedOutputTokensPerItem()))
        );
    }

    @Transactional
    public GeneralSettings update(GeneralSettings settings) {
        validate(settings);
        save(CONFIRMATION_TIMEOUT_SECONDS, settings.publicationConfirmationTimeout().toSeconds());
        save(CONFIRMATION_INTERVAL_SECONDS, settings.publicationConfirmationInterval().toSeconds());
        save(MAX_ITEMS_PER_RUN, settings.publishingMaxItemsPerRun());
        save(MAX_CONTENTS_PER_BATCH, settings.generationMaxContentsPerBatch());
        save(MAX_IMAGES_PER_BATCH, settings.generationMaxImagesPerBatch());
        save(MAX_VIDEOS_PER_BATCH, settings.generationMaxVideosPerBatch());
        save(MAX_ESTIMATED_COST_USD, settings.generationMaxEstimatedCostUsd().toPlainString());
        save(ESTIMATED_INPUT_TOKENS_PER_ITEM, settings.generationEstimatedInputTokensPerItem());
        save(ESTIMATED_OUTPUT_TOKENS_PER_ITEM, settings.generationEstimatedOutputTokensPerItem());
        log.info(
                "General settings updated confirmationTimeoutSeconds={} confirmationIntervalSeconds={} "
                        + "publishingMaxItemsPerRun={} generationMaxContentsPerBatch={} "
                        + "generationMaxImagesPerBatch={} generationMaxVideosPerBatch={} "
                        + "generationMaxEstimatedCostUsd={} generationEstimatedInputTokensPerItem={} "
                        + "generationEstimatedOutputTokensPerItem={}",
                settings.publicationConfirmationTimeout().toSeconds(),
                settings.publicationConfirmationInterval().toSeconds(),
                settings.publishingMaxItemsPerRun(),
                settings.generationMaxContentsPerBatch(),
                settings.generationMaxImagesPerBatch(),
                settings.generationMaxVideosPerBatch(),
                settings.generationMaxEstimatedCostUsd(),
                settings.generationEstimatedInputTokensPerItem(),
                settings.generationEstimatedOutputTokensPerItem()
        );
        return settings;
    }

    private void save(String key, long value) {
        save(key, Long.toString(value));
    }

    private void save(String key, String value) {
        ApplicationSetting setting = repository.findById(key)
                .orElseGet(() -> ApplicationSetting.create(key, value));
        setting.update(value);
        repository.save(setting);
    }

    private static BigDecimal readDecimal(
            Map<String, ApplicationSetting> settings,
            String key,
            BigDecimal fallback
    ) {
        ApplicationSetting setting = settings.get(key);
        return setting == null ? fallback : new BigDecimal(setting.value());
    }

    private static long readLong(
            Map<String, ApplicationSetting> settings,
            String key,
            long fallback
    ) {
        ApplicationSetting setting = settings.get(key);
        return setting == null ? fallback : Long.parseLong(setting.value());
    }

    private static void validate(GeneralSettings settings) {
        long timeoutSeconds = settings.publicationConfirmationTimeout().toSeconds();
        long intervalSeconds = settings.publicationConfirmationInterval().toSeconds();
        if (timeoutSeconds < 60 || timeoutSeconds > 86_400) {
            throw new IllegalArgumentException("Publication confirmation timeout must be between 1 minute and 24 hours");
        }
        if (intervalSeconds < 5 || intervalSeconds > 300) {
            throw new IllegalArgumentException("Publication confirmation interval must be between 5 and 300 seconds");
        }
        if (intervalSeconds >= timeoutSeconds) {
            throw new IllegalArgumentException("Publication confirmation interval must be shorter than timeout");
        }
        if (settings.publishingMaxItemsPerRun() < 1 || settings.publishingMaxItemsPerRun() > 100) {
            throw new IllegalArgumentException("Publishing max items per run must be between 1 and 100");
        }
        if (settings.generationMaxContentsPerBatch() < 1 || settings.generationMaxContentsPerBatch() > 1_000) {
            throw new IllegalArgumentException("Generation max contents per batch must be between 1 and 1000");
        }
        if (settings.generationMaxImagesPerBatch() < 0 || settings.generationMaxImagesPerBatch() > 1_000) {
            throw new IllegalArgumentException("Generation max images per batch must be between 0 and 1000");
        }
        if (settings.generationMaxVideosPerBatch() < 0 || settings.generationMaxVideosPerBatch() > 1_000) {
            throw new IllegalArgumentException("Generation max videos per batch must be between 0 and 1000");
        }
        if (settings.generationMaxEstimatedCostUsd() == null
                || settings.generationMaxEstimatedCostUsd().compareTo(new BigDecimal("0.01")) < 0
                || settings.generationMaxEstimatedCostUsd().compareTo(new BigDecimal("10000.00")) > 0) {
            throw new IllegalArgumentException("Generation max estimated cost must be between 0.01 and 10000 USD");
        }
        if (settings.generationEstimatedInputTokensPerItem() < 1
                || settings.generationEstimatedInputTokensPerItem() > 1_000_000) {
            throw new IllegalArgumentException("Estimated input tokens per item must be between 1 and 1000000");
        }
        if (settings.generationEstimatedOutputTokensPerItem() < 1
                || settings.generationEstimatedOutputTokensPerItem() > 1_000_000) {
            throw new IllegalArgumentException("Estimated output tokens per item must be between 1 and 1000000");
        }
    }
}
