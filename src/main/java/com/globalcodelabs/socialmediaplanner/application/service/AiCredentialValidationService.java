package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiCredentialValidationResult;
import com.globalcodelabs.socialmediaplanner.infrastructure.ai.AiCredentialValidationClient;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.model.ApiCredential;
import com.globalcodelabs.socialmediaplanner.domain.enums.CredentialType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiCredentialValidationService {

    private final ApiCredentialService apiCredentialService;
    private final AiCredentialValidationClient aiCredentialValidationClient;

    public ApiCredential validate(UUID credentialId) {
        ApiCredential credential = apiCredentialService.get(credentialId);
        if (credential.credentialType() != CredentialType.AI_PROVIDER) {
            throw new DomainException("Only AI provider credentials can be validated by this endpoint");
        }

        ResolvedApiCredential resolved = apiCredentialService.resolveActiveIncludingExpired(
                CredentialType.AI_PROVIDER,
                credential.providerName()
        );
        AiCredentialValidationResult result = aiCredentialValidationClient.validate(
                credential.providerName(),
                resolved.accessToken()
        );
        if (result.valid()) {
            return apiCredentialService.markValidationSucceeded(credentialId);
        }
        return apiCredentialService.markValidationFailed(credentialId, result.failureReason());
    }
}
