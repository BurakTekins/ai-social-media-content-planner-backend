package com.globalcodelabs.socialmediaplanner.application.port.out.ai;

public interface AiProviderClientResolver {

    AiProviderClient resolve(String providerName);
}
