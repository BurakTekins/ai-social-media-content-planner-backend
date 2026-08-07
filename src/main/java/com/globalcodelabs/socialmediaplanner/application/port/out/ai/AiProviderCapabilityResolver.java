package com.globalcodelabs.socialmediaplanner.application.port.out.ai;

import com.globalcodelabs.socialmediaplanner.domain.model.AiCapability;

public interface AiProviderCapabilityResolver {

    boolean supports(String providerName, AiCapability capability);
}
