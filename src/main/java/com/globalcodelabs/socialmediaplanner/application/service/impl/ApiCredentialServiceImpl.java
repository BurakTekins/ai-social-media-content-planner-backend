package com.globalcodelabs.socialmediaplanner.application.service.impl;

import com.globalcodelabs.socialmediaplanner.application.command.CreateApiCredentialCommand;
import com.globalcodelabs.socialmediaplanner.application.command.RotateApiCredentialCommand;
import com.globalcodelabs.socialmediaplanner.application.port.out.security.CredentialCipher;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialResolver;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialService;
import com.globalcodelabs.socialmediaplanner.application.service.ResolvedApiCredential;
import com.globalcodelabs.socialmediaplanner.common.exception.ApiCredentialAlreadyExistsException;
import com.globalcodelabs.socialmediaplanner.common.exception.ApiCredentialNotFoundException;
import com.globalcodelabs.socialmediaplanner.common.exception.ApiCredentialUnavailableException;
import com.globalcodelabs.socialmediaplanner.domain.model.ApiCredential;
import com.globalcodelabs.socialmediaplanner.domain.model.CredentialType;
import com.globalcodelabs.socialmediaplanner.domain.repository.ApiCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiCredentialServiceImpl implements ApiCredentialService, ApiCredentialResolver {

    private final ApiCredentialRepository apiCredentialRepository;
    private final CredentialCipher credentialCipher;

    @Override
    @Transactional
    public ApiCredential create(CreateApiCredentialCommand command) {
        String providerName = normalizeProviderName(command.providerName());
        if (apiCredentialRepository.existsByCredentialTypeAndProviderName(
                command.credentialType(), providerName
        )) {
            throw new ApiCredentialAlreadyExistsException(command.credentialType(), providerName);
        }

        ApiCredential credential = ApiCredential.create(
                command.credentialType(),
                providerName,
                command.accountIdentifier(),
                credentialCipher.encrypt(command.accessToken()),
                encryptOptional(command.refreshToken()),
                command.expiresAt()
        );
        try {
            ApiCredential saved = apiCredentialRepository.saveAndFlush(credential);
            log.info("API credential created credentialId={} credentialType={} provider={}",
                    saved.id(), saved.credentialType(), saved.providerName());
            return saved;
        } catch (DataIntegrityViolationException exception) {
            throw new ApiCredentialAlreadyExistsException(command.credentialType(), providerName);
        }
    }

    @Override
    @Transactional
    public ApiCredential updateAccountIdentifier(UUID credentialId, String accountIdentifier) {
        ApiCredential credential = find(credentialId);
        credential.updateAccountIdentifier(accountIdentifier);
        ApiCredential saved = apiCredentialRepository.save(credential);
        log.info("API credential account identifier updated credentialId={} credentialType={} provider={}",
                saved.id(), saved.credentialType(), saved.providerName());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiCredential> list() {
        return apiCredentialRepository.findAllByOrderByCredentialTypeAscProviderNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public ApiCredential get(UUID credentialId) {
        return find(credentialId);
    }

    @Override
    @Transactional
    public ApiCredential rotateTokens(UUID credentialId, RotateApiCredentialCommand command) {
        ApiCredential credential = find(credentialId);
        credential.rotateTokens(
                credentialCipher.encrypt(command.accessToken()),
                encryptOptional(command.refreshToken()),
                command.expiresAt()
        );
        ApiCredential saved = apiCredentialRepository.save(credential);
        log.info("API credential tokens rotated credentialId={} credentialType={} provider={}",
                saved.id(), saved.credentialType(), saved.providerName());
        return saved;
    }

    @Override
    @Transactional
    public ApiCredential changeActive(UUID credentialId, boolean active) {
        ApiCredential credential = find(credentialId);
        credential.changeActive(active);
        ApiCredential saved = apiCredentialRepository.save(credential);
        log.info("API credential active state changed credentialId={} active={} provider={}",
                saved.id(), saved.active(), saved.providerName());
        return saved;
    }

    @Override
    @Transactional
    public void delete(UUID credentialId) {
        ApiCredential credential = find(credentialId);
        apiCredentialRepository.delete(credential);
        log.info("API credential deleted credentialId={} credentialType={} provider={}",
                credential.id(), credential.credentialType(), credential.providerName());
    }

    @Override
    @Transactional(
            readOnly = true,
            noRollbackFor = ApiCredentialUnavailableException.class
    )
    public ResolvedApiCredential resolveActive(
            CredentialType credentialType,
            String providerName
    ) {
        String normalizedProviderName = normalizeProviderName(providerName);
        ApiCredential credential = apiCredentialRepository
                .findByCredentialTypeAndProviderNameAndActiveTrue(
                        credentialType, normalizedProviderName
                )
                .filter(candidate -> !candidate.expiredAt(OffsetDateTime.now()))
                .orElseThrow(() -> new ApiCredentialUnavailableException(
                        credentialType, normalizedProviderName
                ));
        return new ResolvedApiCredential(
                credential.id(),
                credential.credentialType(),
                credential.providerName(),
                credential.accountIdentifier(),
                credentialCipher.decrypt(credential.encryptedAccessToken()),
                decryptOptional(credential.encryptedRefreshToken()),
                credential.expiresAt()
        );
    }

    private ApiCredential find(UUID credentialId) {
        return apiCredentialRepository.findById(credentialId)
                .orElseThrow(() -> new ApiCredentialNotFoundException(credentialId));
    }

    private String encryptOptional(String token) {
        return token == null ? null : credentialCipher.encrypt(token);
    }

    private String decryptOptional(String token) {
        return token == null ? null : credentialCipher.decrypt(token);
    }

    private static String normalizeProviderName(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("Provider name cannot be blank");
        }
        return providerName.trim().toLowerCase(Locale.ROOT);
    }
}
