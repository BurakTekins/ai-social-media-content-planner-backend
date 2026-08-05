package com.globalcodelabs.socialmediaplanner.application.service.impl;

import com.globalcodelabs.socialmediaplanner.application.command.CreateApiCredentialCommand;
import com.globalcodelabs.socialmediaplanner.application.port.out.security.CredentialCipher;
import com.globalcodelabs.socialmediaplanner.application.service.ResolvedApiCredential;
import com.globalcodelabs.socialmediaplanner.domain.model.ApiCredential;
import com.globalcodelabs.socialmediaplanner.domain.model.CredentialType;
import com.globalcodelabs.socialmediaplanner.domain.repository.ApiCredentialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiCredentialServiceImplTest {

    @Mock
    private ApiCredentialRepository apiCredentialRepository;

    @Mock
    private CredentialCipher credentialCipher;

    @InjectMocks
    private ApiCredentialServiceImpl service;

    @Test
    void createsCredentialWithAccountIdentifier() {
        when(apiCredentialRepository.existsByCredentialTypeAndProviderName(
                CredentialType.SOCIAL_PLATFORM, "linkedin"
        )).thenReturn(false);
        when(credentialCipher.encrypt("access-token")).thenReturn("encrypted-access-token");
        when(apiCredentialRepository.saveAndFlush(any(ApiCredential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApiCredential credential = service.create(new CreateApiCredentialCommand(
                CredentialType.SOCIAL_PLATFORM,
                " LinkedIn ",
                " urn:li:person:123 ",
                "access-token",
                null,
                null
        ));

        assertThat(credential.providerName()).isEqualTo("linkedin");
        assertThat(credential.accountIdentifier()).isEqualTo("urn:li:person:123");
        verify(apiCredentialRepository).saveAndFlush(credential);
    }

    @Test
    void updatesAccountIdentifierWithoutChangingCredentialIdentity() {
        ApiCredential credential = createLinkedinCredential();
        UUID credentialId = credential.id();
        when(apiCredentialRepository.findById(credentialId)).thenReturn(Optional.of(credential));
        when(apiCredentialRepository.save(credential)).thenReturn(credential);

        ApiCredential updated = service.updateAccountIdentifier(
                credentialId,
                "urn:li:organization:456"
        );

        assertThat(updated.id()).isEqualTo(credentialId);
        assertThat(updated.credentialType()).isEqualTo(CredentialType.SOCIAL_PLATFORM);
        assertThat(updated.providerName()).isEqualTo("linkedin");
        assertThat(updated.accountIdentifier()).isEqualTo("urn:li:organization:456");
        verify(apiCredentialRepository).save(credential);
    }

    @Test
    void resolvesAccountIdentifierWithDecryptedTokens() {
        ApiCredential credential = createLinkedinCredential();
        when(apiCredentialRepository.findByCredentialTypeAndProviderNameAndActiveTrue(
                CredentialType.SOCIAL_PLATFORM, "linkedin"
        )).thenReturn(Optional.of(credential));
        when(credentialCipher.decrypt("encrypted-access-token")).thenReturn("access-token");

        ResolvedApiCredential resolved = service.resolveActive(
                CredentialType.SOCIAL_PLATFORM,
                " LinkedIn "
        );

        assertThat(resolved.credentialId()).isEqualTo(credential.id());
        assertThat(resolved.providerName()).isEqualTo("linkedin");
        assertThat(resolved.accountIdentifier()).isEqualTo("urn:li:person:123");
        assertThat(resolved.accessToken()).isEqualTo("access-token");
    }

    private static ApiCredential createLinkedinCredential() {
        return ApiCredential.create(
                CredentialType.SOCIAL_PLATFORM,
                "linkedin",
                "urn:li:person:123",
                "encrypted-access-token",
                null,
                null
        );
    }
}
