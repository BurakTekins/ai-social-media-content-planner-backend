package com.globalcodelabs.socialmediaplanner.infrastructure.ai;

import com.globalcodelabs.socialmediaplanner.domain.enums.AiCapability;

import java.util.Locale;
import java.util.Objects;

public record AiGenerationRequest(
        String provider,
        AiCapability capability,
        String prompt,
        String model
) {
    public AiGenerationRequest {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("AI provider cannot be blank");
        }
        Objects.requireNonNull(capability, "AI capability cannot be null");
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("AI prompt cannot be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("AI model cannot be blank");
        }
        provider = provider.trim().toLowerCase(Locale.ROOT);
        prompt = prompt.trim();
        model = model.trim();
    }
}
