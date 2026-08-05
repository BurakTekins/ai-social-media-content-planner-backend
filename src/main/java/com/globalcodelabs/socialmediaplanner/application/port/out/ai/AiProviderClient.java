package com.globalcodelabs.socialmediaplanner.application.port.out.ai;

public interface AiProviderClient {

    String providerName();

    AiGenerationResult generate(AiGenerationRequest request);
}
