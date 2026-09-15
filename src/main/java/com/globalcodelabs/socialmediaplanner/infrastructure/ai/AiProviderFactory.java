package com.globalcodelabs.socialmediaplanner.infrastructure.ai;

import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;
import com.globalcodelabs.socialmediaplanner.domain.enums.AiProvider;
import com.globalcodelabs.socialmediaplanner.infrastructure.IntegrationMode;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.mock.MockAiProviderClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.video.RecoverableVideoProviderClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AiProviderFactory {

    private final AiProviderProperties properties;
    private final MockAiProviderClient mockProviderClient;
    private final Map<String, AiProviderClient> realProviderMap;

    public AiProviderFactory(
            AiProviderProperties properties,
            MockAiProviderClient mockProviderClient,
            List<AiProviderClient> providerClients
    ) {
        this.properties = properties;
        this.mockProviderClient = mockProviderClient;
        this.realProviderMap = providerClients.stream()
                .filter(client -> client != mockProviderClient)
                .collect(Collectors.toUnmodifiableMap(
                        client -> normalize(client.providerName()),
                        Function.identity()
                ));
    }

    public AiProviderClient resolve(String providerName) {
        String normalizedProviderName = normalize(providerName);
        properties.requireProvider(normalizedProviderName);
        if (properties.getMode() == IntegrationMode.MOCK) {
            return mockProviderClient;
        }
        AiProviderClient providerClient = realProviderMap.get(normalizedProviderName);
        if (providerClient == null) {
            throw new IllegalArgumentException("Unsupported real AI provider: " + normalizedProviderName);
        }
        return providerClient;
    }

    public boolean supports(String providerName, AiCapability capability) {
        if (capability == null) {
            return false;
        }
        String normalizedProviderName = normalize(providerName);
        if (!properties.getProviders().containsKey(normalizedProviderName)) {
            return false;
        }
        if (properties.getMode() == IntegrationMode.MOCK) {
            return true;
        }
        return AiProvider.findCanonical(normalizedProviderName)
                .map(provider -> provider.supports(capability))
                .orElse(false);
    }

    public Optional<RecoverableVideoProviderClient> findRecoverableVideo(String providerName) {
        AiProviderClient client = resolve(providerName);
        if (client instanceof RecoverableVideoProviderClient recoverableVideoProviderClient) {
            return Optional.of(recoverableVideoProviderClient);
        }
        return Optional.empty();
    }

    private static String normalize(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("AI provider cannot be blank");
        }
        return providerName.trim().toLowerCase(Locale.ROOT);
    }
}
