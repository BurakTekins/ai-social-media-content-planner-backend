package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.domain.model.ApiCredential;

import java.util.UUID;

public interface AiCredentialValidationService {

    ApiCredential validate(UUID credentialId);
}
