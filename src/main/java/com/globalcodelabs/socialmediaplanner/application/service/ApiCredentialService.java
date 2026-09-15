package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.application.command.CreateApiCredentialCommand;
import com.globalcodelabs.socialmediaplanner.application.command.ConnectSocialCredentialCommand;
import com.globalcodelabs.socialmediaplanner.application.command.RotateApiCredentialCommand;
import com.globalcodelabs.socialmediaplanner.application.command.RefreshApiCredentialCommand;
import com.globalcodelabs.socialmediaplanner.infrastructure.encryption.AesGcmCredentialCipher;
import com.globalcodelabs.socialmediaplanner.common.exception.ApiCredentialAlreadyExistsException;
import com.globalcodelabs.socialmediaplanner.common.exception.ApiCredentialNotFoundException;
import com.globalcodelabs.socialmediaplanner.common.exception.ApiCredentialUnavailableException;
import com.globalcodelabs.socialmediaplanner.domain.model.ApiCredential;
import com.globalcodelabs.socialmediaplanner.domain.enums.CredentialType;
import com.globalcodelabs.socialmediaplanner.domain.repository.ApiCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiCredentialService {

    private final ApiCredentialRepository apiCredentialRepository;
    private final AesGcmCredentialCipher credentialCipher;

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

    @Transactional
    public ApiCredential connectSocialAccount(ConnectSocialCredentialCommand command) {
        String providerName = normalizeProviderName(command.providerName());
        Optional<ApiCredential> existingCredential = apiCredentialRepository
                .findByCredentialTypeAndProviderName(CredentialType.SOCIAL_PLATFORM, providerName);
        ApiCredential credential = existingCredential
                .orElseGet(() -> ApiCredential.create(
                        CredentialType.SOCIAL_PLATFORM,
                        providerName,
                        command.accountIdentifier(),
                        credentialCipher.encrypt(command.accessToken()),
                        encryptOptional(command.refreshToken()),
                        command.expiresAt()
                ));

        if (existingCredential.isPresent()) {
            credential.updateAccountIdentifier(command.accountIdentifier());
            credential.rotateTokens(
                    credentialCipher.encrypt(command.accessToken()),
                    encryptOptional(command.refreshToken()),
                    command.expiresAt()
            );
        }
        credential.markValidated(
                command.accountDisplayName(),
                command.grantedScopes(),
                command.refreshTokenExpiresAt()
        );
        ApiCredential saved = apiCredentialRepository.save(credential);
        log.info("Social platform credential connected credentialId={} provider={}",
                saved.id(), saved.providerName());
        return saved;
    }

    @Transactional
    public ApiCredential updateAccountIdentifier(UUID credentialId, String accountIdentifier) {
        ApiCredential credential = find(credentialId);
        credential.updateAccountIdentifier(accountIdentifier);
        ApiCredential saved = apiCredentialRepository.save(credential);
        log.info("API credential account identifier updated credentialId={} credentialType={} provider={}",
                saved.id(), saved.credentialType(), saved.providerName());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ApiCredential> list() {
        return apiCredentialRepository.findAllByOrderByCredentialTypeAscProviderNameAsc();
    }

    @Transactional(readOnly = true)
    public ApiCredential get(UUID credentialId) {
        return find(credentialId);
    }

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

    @Transactional
    public ApiCredential refreshTokens(UUID credentialId, RefreshApiCredentialCommand command) {
        ApiCredential credential = find(credentialId);
        credential.refreshTokens(
                credentialCipher.encrypt(command.accessToken()),
                encryptOptional(command.refreshToken()),
                command.expiresAt(),
                command.refreshTokenExpiresAt()
        );
        ApiCredential saved = apiCredentialRepository.save(credential);
        log.info("Social platform credential tokens refreshed credentialId={} provider={}",
                saved.id(), saved.providerName());
        return saved;
    }

    @Transactional
    public ApiCredential changeActive(UUID credentialId, boolean active) {
        ApiCredential credential = find(credentialId);
        credential.changeActive(active);
        ApiCredential saved = apiCredentialRepository.save(credential);
        log.info("API credential active state changed credentialId={} active={} provider={}",
                saved.id(), saved.active(), saved.providerName());
        return saved;
    }

    @Transactional
    public ApiCredential markValidationSucceeded(UUID credentialId) {
        ApiCredential credential = find(credentialId);
        credential.markValidated(null, Set.of(), null);
        ApiCredential saved = apiCredentialRepository.save(credential);
        log.info("API credential validation succeeded credentialId={} credentialType={} provider={}",
                saved.id(), saved.credentialType(), saved.providerName());
        return saved;
    }

    @Transactional
    public ApiCredential markValidationFailed(UUID credentialId, String failureReason) {
        ApiCredential credential = find(credentialId);
        credential.markValidationFailed(failureReason);
        ApiCredential saved = apiCredentialRepository.save(credential);
        log.warn("API credential validation failed credentialId={} credentialType={} provider={}",
                saved.id(), saved.credentialType(), saved.providerName());
        return saved;
    }

    @Transactional
    public void delete(UUID credentialId) {
        ApiCredential credential = find(credentialId);
        apiCredentialRepository.delete(credential);
        log.info("API credential deleted credentialId={} credentialType={} provider={}",
                credential.id(), credential.credentialType(), credential.providerName());
    }

    @Transactional(
            readOnly = true,
            noRollbackFor = ApiCredentialUnavailableException.class
    )
    public ResolvedApiCredential resolveActive(
            CredentialType credentialType,
            String providerName
    ) {
        String normalizedProviderName = normalizeProviderName(providerName);
        ApiCredential credential = findActive(credentialType, normalizedProviderName)
                .filter(candidate -> !candidate.expiredAt(OffsetDateTime.now(ZoneOffset.UTC)))
                .orElseThrow(() -> new ApiCredentialUnavailableException(
                        credentialType, normalizedProviderName
                ));
        return resolve(credential);
    }

    @Transactional(readOnly = true)
    public ResolvedApiCredential resolveActiveIncludingExpired(
            CredentialType credentialType,
            String providerName
    ) {
        String normalizedProviderName = normalizeProviderName(providerName);
        ApiCredential credential = findActive(credentialType, normalizedProviderName)
                .orElseThrow(() -> new ApiCredentialUnavailableException(
                        credentialType, normalizedProviderName
                ));
        return resolve(credential);
    }

    private Optional<ApiCredential> findActive(
            CredentialType credentialType,
            String normalizedProviderName
    ) {
        return apiCredentialRepository.findByCredentialTypeAndProviderNameAndActiveTrue(
                credentialType, normalizedProviderName
        );
    }

    private ResolvedApiCredential resolve(ApiCredential credential) {
        return new ResolvedApiCredential(
                credential.id(),
                credential.credentialType(),
                credential.providerName(),
                credential.accountIdentifier(),
                credentialCipher.decrypt(credential.encryptedAccessToken()),
                decryptOptional(credential.encryptedRefreshToken()),
                credential.expiresAt(),
                credential.refreshTokenExpiresAt()
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
