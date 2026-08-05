package com.globalcodelabs.socialmediaplanner.infrastructure.ai;

import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiProviderClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.config.AiProviderProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.mock.MockAiProviderClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.provider.ClaudeProviderClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.provider.DeepSeekProviderClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.provider.GeminiProviderClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.provider.OpenAiProviderClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.provider.QwenProviderClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            OpenAiProviderClient openAiProviderClient,
            ClaudeProviderClient claudeProviderClient,
            GeminiProviderClient geminiProviderClient,
            DeepSeekProviderClient deepSeekProviderClient,
            QwenProviderClient qwenProviderClient
    ) {
        this.properties = properties;
        this.mockProviderClient = mockProviderClient;
        this.realProviderMap = List.<AiProviderClient>of(
                openAiProviderClient,
                claudeProviderClient,
                geminiProviderClient,
                deepSeekProviderClient,
                qwenProviderClient
        ).stream().collect(Collectors.toUnmodifiableMap(
                client -> normalize(client.providerName()),
                Function.identity()
        ));
    }

    public AiProviderClient resolve(String providerName) {
        String normalizedProviderName = normalize(providerName);
        properties.requireProvider(normalizedProviderName);
        if ("mock".equalsIgnoreCase(properties.getMode())) {
            return mockProviderClient;
        }
        AiProviderClient providerClient = realProviderMap.get(normalizedProviderName);
        if (providerClient == null) {
            throw new IllegalArgumentException("Unsupported real AI provider: " + normalizedProviderName);
        }
        return providerClient;
    }

    private static String normalize(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("AI provider cannot be blank");
        }
        return providerName.trim().toLowerCase(Locale.ROOT);
    }
}
