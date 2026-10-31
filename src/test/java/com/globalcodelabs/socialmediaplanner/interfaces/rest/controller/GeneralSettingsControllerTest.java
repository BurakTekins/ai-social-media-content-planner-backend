package com.globalcodelabs.socialmediaplanner.interfaces.rest.controller;

import com.globalcodelabs.socialmediaplanner.application.service.GeneralSettings;
import com.globalcodelabs.socialmediaplanner.application.service.GeneralSettingsService;
import com.globalcodelabs.socialmediaplanner.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.time.Duration;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GeneralSettingsControllerTest {
    private final GeneralSettingsService service = mock(GeneralSettingsService.class);
    private MockMvc mvc;
    private static final String VALID = """
        {"publicationConfirmationTimeoutSeconds":120,"publicationConfirmationIntervalSeconds":30,
         "publishingMaxItemsPerRun":10,"generationMaxContentsPerBatch":50,
         "generationMaxImagesPerBatch":0,"generationMaxVideosPerBatch":0,
         "generationMaxEstimatedCostUsd":12.34,"generationEstimatedInputTokensPerItem":2000,
         "generationEstimatedOutputTokensPerItem":300}
        """;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new GeneralSettingsController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void validUpdateConvertsSecondsAndReturnsSavedSettings() throws Exception {
        var settings = new GeneralSettings(Duration.ofSeconds(120), Duration.ofSeconds(30),
                10, 50, 0, 0, new BigDecimal("12.34"), 2000, 300);
        when(service.update(settings)).thenReturn(settings);
        mvc.perform(put("/api/settings/general").contentType("application/json").content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicationConfirmationTimeoutSeconds").value(120))
                .andExpect(jsonPath("$.generationMaxEstimatedCostUsd").value(12.34));
        verify(service).update(settings);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "null"})
    void missingOrMalformedInputNeverReachesService(String body) throws Exception {
        mvc.perform(put("/api/settings/general").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void intervalMustBeShorterThanTimeout() throws Exception {
        mvc.perform(put("/api/settings/general").contentType("application/json")
                        .content(VALID.replace(":30", ":120")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void invalidBudgetIsRejectedAtHttpBoundary() throws Exception {
        mvc.perform(put("/api/settings/general").contentType("application/json")
                        .content(VALID.replace("12.34", "0")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
