package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.domain.model.ApplicationSetting;
import com.globalcodelabs.socialmediaplanner.domain.repository.ApplicationSettingRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.budget.GenerationBudgetProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.publishing.PublishingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeneralSettingsServiceTest {
    private final ApplicationSettingRepository repository = mock(ApplicationSettingRepository.class);
    private final PublishingProperties publishing = new PublishingProperties();
    private final GenerationBudgetProperties budget = new GenerationBudgetProperties();
    private final GeneralSettingsService service = new GeneralSettingsService(repository, publishing, budget);

    @Test
    void missingSettingsUseConfiguredDefaults() {
        when(repository.findAllById(any())).thenReturn(List.of());
        var result = service.get();
        assertThat(result.publicationConfirmationTimeout()).isEqualTo(publishing.getJob().getConfirmationTimeout());
        assertThat(result.generationMaxEstimatedCostUsd()).isEqualByComparingTo(budget.getMaxEstimatedCostUsd());
        assertThat(result.generationMaxContentsPerBatch()).isEqualTo(budget.getMaxContentsPerBatch());
        verify(repository, never()).save(any());
    }

    @Test
    void persistedValuesOverrideDefaultsIndividually() {
        when(repository.findAllById(any())).thenReturn(List.of(
                ApplicationSetting.create("generation.max-estimated-cost-usd", "12.34"),
                ApplicationSetting.create("publishing.confirmation-timeout-seconds", "180")));
        var result = service.get();
        assertThat(result.generationMaxEstimatedCostUsd()).isEqualByComparingTo("12.34");
        assertThat(result.publicationConfirmationTimeout()).isEqualTo(Duration.ofMinutes(3));
        assertThat(result.generationMaxContentsPerBatch()).isEqualTo(budget.getMaxContentsPerBatch());
    }

    @ParameterizedTest
    @MethodSource("invalidSettings")
    void invalidLimitsAreRejectedBeforeAnyPersistence(GeneralSettings settings) {
        assertThatIllegalArgumentException().isThrownBy(() -> service.update(settings));
        verifyNoInteractions(repository);
    }

    static Stream<GeneralSettings> invalidSettings() {
        return Stream.of(
            settings(59, 5, 1, 1, 0, 0, "0.01", 1, 1),
            settings(86401, 5, 1, 1, 0, 0, "0.01", 1, 1),
            settings(60, 4, 1, 1, 0, 0, "0.01", 1, 1),
            settings(600, 301, 1, 1, 0, 0, "0.01", 1, 1),
            settings(60, 60, 1, 1, 0, 0, "0.01", 1, 1),
            settings(60, 5, 0, 1, 0, 0, "0.01", 1, 1),
            settings(60, 5, 101, 1, 0, 0, "0.01", 1, 1),
            settings(60, 5, 1, 0, 0, 0, "0.01", 1, 1),
            settings(60, 5, 1, 1001, 0, 0, "0.01", 1, 1),
            settings(60, 5, 1, 1, -1, 0, "0.01", 1, 1),
            settings(60, 5, 1, 1, 1001, 0, "0.01", 1, 1),
            settings(60, 5, 1, 1, 0, -1, "0.01", 1, 1),
            settings(60, 5, 1, 1, 0, 1001, "0.01", 1, 1),
            settings(60, 5, 1, 1, 0, 0, "0.009", 1, 1),
            settings(60, 5, 1, 1, 0, 0, "10000.01", 1, 1),
            settings(60, 5, 1, 1, 0, 0, null, 1, 1),
            settings(60, 5, 1, 1, 0, 0, "0.01", 0, 1),
            settings(60, 5, 1, 1, 0, 0, "0.01", 1000001, 1),
            settings(60, 5, 1, 1, 0, 0, "0.01", 1, 0),
            settings(60, 5, 1, 1, 0, 0, "0.01", 1, 1000001));
    }

    static GeneralSettings settings(long timeout, long interval, int items, int contents,
                                    int images, int videos, String cost, int input, int output) {
        return new GeneralSettings(Duration.ofSeconds(timeout), Duration.ofSeconds(interval), items,
                contents, images, videos, cost == null ? null : new BigDecimal(cost), input, output);
    }

    @Test
    void validBoundaryValuesAreSavedWithoutLosingDecimalPrecision() {
        var settings = settings(86400, 300, 100, 1000, 0, 1000, "10000.00", 1000000, 1000000);
        assertThat(service.update(settings)).isEqualTo(settings);
        var saved = org.mockito.ArgumentCaptor.forClass(ApplicationSetting.class);
        verify(repository, times(9)).save(saved.capture());
        assertThat(saved.getAllValues()).filteredOn(s -> s.key().equals("generation.max-estimated-cost-usd"))
                .extracting(ApplicationSetting::value).containsExactly("10000.00");
    }
}
