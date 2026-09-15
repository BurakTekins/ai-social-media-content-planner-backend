package com.globalcodelabs.socialmediaplanner.infrastructure.ai.image;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

@Component
public class ImageModelPricingRegistry {

    private final Map<ModelKey, Integer> outputTokensPerItem;

    public ImageModelPricingRegistry(ImageGenerationProperties properties) {
        Map<ModelKey, Integer> configuredModels = new LinkedHashMap<>();
        for (ImageGenerationProperties.Model configured : properties.getModels()) {
            ModelKey key = new ModelKey(
                    normalize(configured.getProvider()),
                    normalize(configured.getModel())
            );
            Integer previous = configuredModels.put(
                    key,
                    configured.getEstimatedOutputTokensPerItem()
            );
            if (previous != null) {
                throw new IllegalStateException("Duplicate image pricing registry entry: " + key);
            }
        }
        this.outputTokensPerItem = Map.copyOf(configuredModels);
    }

    public OptionalInt estimatedOutputTokensPerItem(String provider, String model) {
        if (provider == null || provider.isBlank() || model == null || model.isBlank()) {
            return OptionalInt.empty();
        }
        Integer tokens = outputTokensPerItem.get(new ModelKey(normalize(provider), normalize(model)));
        return tokens == null ? OptionalInt.empty() : OptionalInt.of(tokens);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record ModelKey(String provider, String model) {
    }
}
