package com.globalcodelabs.socialmediaplanner.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalcodelabs.socialmediaplanner.application.port.out.aimodel.ModelsDevCatalogClient.ModelData;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.model.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.AiModelCache;
import com.globalcodelabs.socialmediaplanner.domain.repository.AiModelCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelServiceImplTest {

    private static final OffsetDateTime INITIAL_SYNCED_AT =
            OffsetDateTime.parse("2026-07-29T03:00:00+03:00");
    private static final OffsetDateTime NEW_SYNCED_AT =
            OffsetDateTime.parse("2026-07-30T03:00:00+03:00");

    @Mock
    private AiModelCacheRepository aiModelCacheRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiModelServiceImpl aiModelService;

    @BeforeEach
    void setUp() {
        aiModelService = new AiModelServiceImpl(aiModelCacheRepository);
    }

    @Test
    void updatesExistingModelsAndInsertsNewModelsInOneSave() throws Exception {
        AiModelCache existing = AiModelCache.create(
                "openai",
                "gpt-model",
                "Old display name",
                AiCapability.TEXT,
                objectMapper.readTree("{\"version\":1}"),
                INITIAL_SYNCED_AT
        );
        when(aiModelCacheRepository.findAllByProviderNameIn(anyCollection()))
                .thenReturn(List.of(existing));

        List<ModelData> models = List.of(
                new ModelData(
                        "openai",
                        "gpt-model",
                        "New display name",
                        AiCapability.TEXT,
                        objectMapper.readTree("{\"version\":2}")
                ),
                new ModelData(
                        "openai",
                        "gpt-model",
                        "New display name",
                        AiCapability.IMAGE,
                        objectMapper.readTree("{\"version\":2}")
                ),
                new ModelData(
                        "gemini",
                        "veo-model",
                        "Veo model",
                        AiCapability.VIDEO,
                        objectMapper.readTree("{\"version\":1}")
                )
        );

        int synchronizedCount = aiModelService.synchronize(models, NEW_SYNCED_AT);

        assertThat(synchronizedCount).isEqualTo(3);
        ArgumentCaptor<List<AiModelCache>> modelsCaptor = ArgumentCaptor.captor();
        verify(aiModelCacheRepository).saveAll(modelsCaptor.capture());
        assertThat(modelsCaptor.getValue()).hasSize(3);

        AiModelCache updated = modelsCaptor.getValue().stream()
                .filter(model -> model.modelId().equals("gpt-model")
                        && model.capability() == AiCapability.TEXT)
                .findFirst()
                .orElseThrow();
        assertThat(updated).isSameAs(existing);
        assertThat(updated.displayName()).isEqualTo("New display name");
        assertThat(updated.capability()).isEqualTo(AiCapability.TEXT);
        assertThat(updated.rawMetadata().path("version").asInt()).isEqualTo(2);
        assertThat(updated.lastSyncedAt()).isEqualTo(NEW_SYNCED_AT);

        AiModelCache imageCapability = modelsCaptor.getValue().stream()
                .filter(model -> model.modelId().equals("gpt-model")
                        && model.capability() == AiCapability.IMAGE)
                .findFirst()
                .orElseThrow();
        assertThat(imageCapability).isNotSameAs(existing);
        assertThat(imageCapability.id()).isNotNull();

        AiModelCache created = modelsCaptor.getValue().stream()
                .filter(model -> model.modelId().equals("veo-model"))
                .findFirst()
                .orElseThrow();
        assertThat(created.id()).isNotNull();
        assertThat(created.providerName()).isEqualTo("gemini");
        assertThat(created.lastSyncedAt()).isEqualTo(NEW_SYNCED_AT);
    }

    @Test
    void mapsModelsDevProviderAliasesForEndpointFilters() {
        when(aiModelCacheRepository.findAllByFilters(null, "gemini"))
                .thenReturn(List.of());

        aiModelService.findAll(null, "google");

        verify(aiModelCacheRepository).findAllByFilters(null, "gemini");
    }

    @Test
    void rejectsDuplicateModelsWithoutSavingAnything() throws Exception {
        ModelData model = new ModelData(
                "openai",
                "gpt-model",
                "GPT model",
                AiCapability.TEXT,
                objectMapper.readTree("{}")
        );
        when(aiModelCacheRepository.findAllByProviderNameIn(anyCollection()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> aiModelService.synchronize(
                List.of(model, model),
                NEW_SYNCED_AT
        ))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Duplicate AI model capability");

        verify(aiModelCacheRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void rejectsEmptySyncWithoutReadingOrUpdatingCache() {
        assertThatThrownBy(() -> aiModelService.synchronize(List.of(), NEW_SYNCED_AT))
                .isInstanceOf(DomainException.class);

        verifyNoInteractions(aiModelCacheRepository);
    }
}
