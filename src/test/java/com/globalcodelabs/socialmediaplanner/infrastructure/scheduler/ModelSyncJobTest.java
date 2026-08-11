package com.globalcodelabs.socialmediaplanner.infrastructure.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalcodelabs.socialmediaplanner.infrastructure.aimodel.ModelData;
import com.globalcodelabs.socialmediaplanner.infrastructure.aimodel.ModelsDevClient;
import com.globalcodelabs.socialmediaplanner.application.service.AiModelService;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelSyncJobTest {

    @Mock
    private ModelsDevClient modelsDevClient;

    @Mock
    private AiModelService aiModelService;

    private ModelSyncJob modelSyncJob;

    @BeforeEach
    void setUp() {
        modelSyncJob = new ModelSyncJob(modelsDevClient, aiModelService);
    }

    @Test
    void startupSyncFetchesCatalogAndUpsertsModels() throws Exception {
        ModelData model = new ModelData(
                "openai",
                "gpt-model",
                "GPT model",
                AiCapability.TEXT,
                new ObjectMapper().readTree("{}")
        );
        when(modelsDevClient.fetchAll()).thenReturn(List.of(model));
        when(aiModelService.synchronize(eq(List.of(model)), any())).thenReturn(1);

        modelSyncJob.run(null);

        verify(aiModelService).synchronize(eq(List.of(model)), any());
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void fetchFailureDoesNotCallServiceAndDoesNotEscapeJob() {
        when(modelsDevClient.fetchAll())
                .thenThrow(new IllegalStateException("models.dev is unavailable"));

        modelSyncJob.synchronizeDaily();

        verifyNoInteractions(aiModelService);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
