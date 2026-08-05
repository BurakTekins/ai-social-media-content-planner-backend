package com.globalcodelabs.socialmediaplanner.application.service.impl;

import com.globalcodelabs.socialmediaplanner.application.port.out.aimodel.ModelsDevCatalogClient.ModelData;
import com.globalcodelabs.socialmediaplanner.application.service.AiModelService;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.model.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.model.AiModelCache;
import com.globalcodelabs.socialmediaplanner.domain.repository.AiModelCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiModelServiceImpl implements AiModelService {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of(
            "openai",
            "anthropic",
            "gemini",
            "deepseek",
            "qwen"
    );

    private final AiModelCacheRepository aiModelCacheRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AiModelCache> findAll(AiCapability capability, String provider) {
        return aiModelCacheRepository.findAllByFilters(
                capability,
                normalizeProviderFilter(provider)
        );
    }

    @Override
    @Transactional
    public int synchronize(List<ModelData> models, OffsetDateTime syncedAt) {
        if (models == null || models.isEmpty()) {
            throw new DomainException("AI model sync result cannot be empty");
        }
        Objects.requireNonNull(syncedAt, "Synced time cannot be null");

        Set<String> providerNames = new HashSet<>();
        for (ModelData model : models) {
            providerNames.add(normalizeProvider(model.providerName()));
        }

        Map<ModelKey, AiModelCache> existingModels = new HashMap<>();
        for (AiModelCache model : aiModelCacheRepository.findAllByProviderNameIn(providerNames)) {
            existingModels.put(
                    new ModelKey(model.providerName(), model.modelId(), model.capability()),
                    model
            );
        }

        Set<ModelKey> synchronizedKeys = new HashSet<>();
        List<AiModelCache> modelsToSave = new ArrayList<>(models.size());
        for (ModelData model : models) {
            String providerName = normalizeProvider(model.providerName());
            ModelKey key = new ModelKey(providerName, model.modelId(), model.capability());
            if (!synchronizedKeys.add(key)) {
                throw new DomainException(
                        "Duplicate AI model capability in sync result: "
                                + providerName + "/" + model.modelId() + "/" + model.capability()
                );
            }

            AiModelCache cachedModel = existingModels.get(key);
            if (cachedModel == null) {
                cachedModel = AiModelCache.create(
                        providerName,
                        model.modelId(),
                        model.displayName(),
                        model.capability(),
                        model.rawMetadata(),
                        syncedAt
                );
            } else {
                cachedModel.refresh(
                        model.displayName(),
                        model.rawMetadata(),
                        syncedAt
                );
            }
            modelsToSave.add(cachedModel);
        }

        aiModelCacheRepository.saveAll(modelsToSave);
        return modelsToSave.size();
    }

    private static String normalizeProviderFilter(String provider) {
        if (provider == null) {
            return null;
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new DomainException("Provider filter cannot be blank");
        }
        return normalizeProviderAlias(normalized);
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new DomainException("Provider name cannot be blank");
        }
        return normalizeProviderAlias(provider.trim().toLowerCase(Locale.ROOT));
    }

    private static String normalizeProviderAlias(String provider) {
        String canonicalProvider = switch (provider) {
            case "claude" -> "anthropic";
            case "google" -> "gemini";
            case "alibaba" -> "qwen";
            default -> provider;
        };
        if (!SUPPORTED_PROVIDERS.contains(canonicalProvider)) {
            throw new DomainException("Unsupported AI provider: " + provider);
        }
        return canonicalProvider;
    }

    private record ModelKey(
            String providerName,
            String modelId,
            AiCapability capability
    ) {
    }
}
