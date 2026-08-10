package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.application.command.CreateApiCredentialCommand;
import com.globalcodelabs.socialmediaplanner.application.command.ConnectSocialCredentialCommand;
import com.globalcodelabs.socialmediaplanner.application.command.RotateApiCredentialCommand;
import com.globalcodelabs.socialmediaplanner.application.command.RefreshApiCredentialCommand;
import com.globalcodelabs.socialmediaplanner.domain.model.ApiCredential;

import java.util.List;
import java.util.UUID;

public interface ApiCredentialService {

    ApiCredential create(CreateApiCredentialCommand command);

    ApiCredential connectSocialAccount(ConnectSocialCredentialCommand command);

    List<ApiCredential> list();

    ApiCredential get(UUID credentialId);

    ApiCredential rotateTokens(UUID credentialId, RotateApiCredentialCommand command);

    ApiCredential refreshTokens(UUID credentialId, RefreshApiCredentialCommand command);

    ApiCredential updateAccountIdentifier(UUID credentialId, String accountIdentifier);

    ApiCredential changeActive(UUID credentialId, boolean active);

    ApiCredential markValidationSucceeded(UUID credentialId);

    ApiCredential markValidationFailed(UUID credentialId, String failureReason);

    void delete(UUID credentialId);
}
