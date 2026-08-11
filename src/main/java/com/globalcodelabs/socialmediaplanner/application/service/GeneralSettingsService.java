package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.domain.model.ApplicationSetting;
import com.globalcodelabs.socialmediaplanner.domain.repository.ApplicationSettingRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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

    private final ApplicationSettingRepository repository;
    private final PublishingProperties defaults;

    @Transactional(readOnly = true)
    public GeneralSettings get() {
        Map<String, ApplicationSetting> settings = repository.findAllById(java.util.List.of(
                CONFIRMATION_TIMEOUT_SECONDS, CONFIRMATION_INTERVAL_SECONDS, MAX_ITEMS_PER_RUN
        )).stream().collect(Collectors.toMap(ApplicationSetting::key, Function.identity()));
        return new GeneralSettings(
                Duration.ofSeconds(readLong(settings, CONFIRMATION_TIMEOUT_SECONDS,
                        defaults.getJob().getConfirmationTimeout().toSeconds())),
                Duration.ofSeconds(readLong(settings, CONFIRMATION_INTERVAL_SECONDS,
                        defaults.getJob().getConfirmationInterval().toSeconds())),
                Math.toIntExact(readLong(settings, MAX_ITEMS_PER_RUN,
                        defaults.getJob().getMaxItemsPerRun()))
        );
    }

    @Transactional
    public GeneralSettings update(GeneralSettings settings) {
        validate(settings);
        save(CONFIRMATION_TIMEOUT_SECONDS, settings.publicationConfirmationTimeout().toSeconds());
        save(CONFIRMATION_INTERVAL_SECONDS, settings.publicationConfirmationInterval().toSeconds());
        save(MAX_ITEMS_PER_RUN, settings.publishingMaxItemsPerRun());
        log.info(
                "General publishing settings updated confirmationTimeoutSeconds={} confirmationIntervalSeconds={} maxItemsPerRun={}",
                settings.publicationConfirmationTimeout().toSeconds(),
                settings.publicationConfirmationInterval().toSeconds(),
                settings.publishingMaxItemsPerRun()
        );
        return settings;
    }

    private void save(String key, long value) {
        ApplicationSetting setting = repository.findById(key)
                .orElseGet(() -> ApplicationSetting.create(key, Long.toString(value)));
        setting.update(Long.toString(value));
        repository.save(setting);
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
    }
}
