package com.globalcodelabs.socialmediaplanner.application.service.impl;

import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiCredentialValidationResult;
import com.globalcodelabs.socialmediaplanner.application.port.out.ai.AiCredentialValidator;
import com.globalcodelabs.socialmediaplanner.application.service.AiCredentialValidationService;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialResolver;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialService;
import com.globalcodelabs.socialmediaplanner.application.service.ResolvedApiCredential;
import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.model.ApiCredential;
import com.globalcodelabs.socialmediaplanner.domain.model.CredentialType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiCredentialValidationServiceImpl implements AiCredentialValidationService {

    private final ApiCredentialService apiCredentialService;
    private final ApiCredentialResolver apiCredentialResolver;
    private final AiCredentialValidator aiCredentialValidator;

    @Override
    public ApiCredential validate(UUID credentialId) {
        ApiCredential credential = apiCredentialService.get(credentialId);
        if (credential.credentialType() != CredentialType.AI_PROVIDER) {
            throw new DomainException("Only AI provider credentials can be validated by this endpoint");
        }

        ResolvedApiCredential resolved = apiCredentialResolver.resolveActiveIncludingExpired(
                CredentialType.AI_PROVIDER,
                credential.providerName()
        );
        AiCredentialValidationResult result = aiCredentialValidator.validate(
                credential.providerName(),
                resolved.accessToken()
        );
        if (result.valid()) {
            return apiCredentialService.markValidationSucceeded(credentialId);
        }
        return apiCredentialService.markValidationFailed(credentialId, result.failureReason());
    }
}
