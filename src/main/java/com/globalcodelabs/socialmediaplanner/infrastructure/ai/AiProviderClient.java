package com.globalcodelabs.socialmediaplanner.infrastructure.ai;

public interface AiProviderClient {

    String providerName();

    AiGenerationResult generate(AiGenerationRequest request);
}
