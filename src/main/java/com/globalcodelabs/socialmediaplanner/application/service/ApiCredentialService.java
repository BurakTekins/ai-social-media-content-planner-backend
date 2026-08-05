package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.application.command.CreateApiCredentialCommand;
import com.globalcodelabs.socialmediaplanner.application.command.RotateApiCredentialCommand;
import com.globalcodelabs.socialmediaplanner.domain.model.ApiCredential;

import java.util.List;
import java.util.UUID;

public interface ApiCredentialService {

    ApiCredential create(CreateApiCredentialCommand command);

    List<ApiCredential> list();

    ApiCredential get(UUID credentialId);

    ApiCredential rotateTokens(UUID credentialId, RotateApiCredentialCommand command);

    ApiCredential updateAccountIdentifier(UUID credentialId, String accountIdentifier);

    ApiCredential changeActive(UUID credentialId, boolean active);

    void delete(UUID credentialId);
}
