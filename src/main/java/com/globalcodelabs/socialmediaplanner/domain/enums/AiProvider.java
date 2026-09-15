package com.globalcodelabs.socialmediaplanner.domain.enums;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum AiProvider {
    OPENAI("openai", "OpenAI", "openai", Set.of(), Set.of(AiCapability.TEXT, AiCapability.IMAGE)),
    ANTHROPIC("anthropic", "Anthropic", "anthropic", Set.of("claude"), Set.of(AiCapability.TEXT)),
    GEMINI("gemini", "Gemini", "google", Set.of("google"),
            Set.of(AiCapability.TEXT, AiCapability.IMAGE, AiCapability.VIDEO)),
    DEEPSEEK("deepseek", "DeepSeek", "deepseek", Set.of(), Set.of(AiCapability.TEXT)),
    QWEN("qwen", "Qwen", "alibaba", Set.of("alibaba"),
            Set.of(AiCapability.TEXT, AiCapability.IMAGE, AiCapability.VIDEO));

    private static final Map<String, AiProvider> PROVIDERS_BY_NAME = providersByName();
    private static final Map<String, String> MODELS_DEV_PROVIDER_NAMES = buildModelsDevProviderNames();

    private final String canonicalName;
    private final String displayName;
    private final String modelsDevProviderName;
    private final Set<String> aliases;
    private final Set<AiCapability> capabilities;

    AiProvider(
            String canonicalName,
            String displayName,
            String modelsDevProviderName,
            Set<String> aliases,
            Set<AiCapability> capabilities
    ) {
        this.canonicalName = canonicalName;
        this.displayName = displayName;
        this.modelsDevProviderName = modelsDevProviderName;
        this.aliases = aliases;
        this.capabilities = capabilities;
    }

    public String canonicalName() {
        return canonicalName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean supports(AiCapability capability) {
        return capabilities.contains(capability);
    }

    public static Optional<AiProvider> find(String providerName) {
        return normalized(providerName).map(PROVIDERS_BY_NAME::get);
    }

    public static Optional<AiProvider> findCanonical(String providerName) {
        return normalized(providerName).flatMap(name -> Optional
                .ofNullable(PROVIDERS_BY_NAME.get(name))
                .filter(provider -> name.equals(provider.canonicalName)));
    }

    public static String canonicalize(String providerName) {
        String normalized = providerName.trim().toLowerCase(Locale.ROOT);
        AiProvider provider = PROVIDERS_BY_NAME.get(normalized);
        return provider == null ? normalized : provider.canonicalName;
    }

    public static Map<String, String> modelsDevProviderNames() {
        return MODELS_DEV_PROVIDER_NAMES;
    }

    private static Optional<String> normalized(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(providerName.trim().toLowerCase(Locale.ROOT));
    }

    private static Map<String, AiProvider> providersByName() {
        Map<String, AiProvider> providers = new LinkedHashMap<>();
        for (AiProvider provider : values()) {
            providers.put(provider.canonicalName, provider);
            provider.aliases.forEach(alias -> providers.put(alias, provider));
        }
        return Map.copyOf(providers);
    }

    private static Map<String, String> buildModelsDevProviderNames() {
        Map<String, String> providers = new LinkedHashMap<>();
        for (AiProvider provider : values()) {
            providers.put(provider.modelsDevProviderName, provider.canonicalName);
        }
        return Collections.unmodifiableMap(providers);
    }
}
