package com.globalcodelabs.socialmediaplanner.domain.model;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "api_credential")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiCredential {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false, length = 30, updatable = false)
    private CredentialType credentialType;

    @Column(name = "provider_name", nullable = false, updatable = false)
    private String providerName;

    @Column(name = "account_identifier", columnDefinition = "TEXT")
    private String accountIdentifier;

    @Column(name = "encrypted_access_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedAccessToken;

    @Column(name = "encrypted_refresh_token", columnDefinition = "TEXT")
    private String encryptedRefreshToken;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private ApiCredential(
            CredentialType credentialType,
            String providerName,
            String accountIdentifier,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            OffsetDateTime expiresAt
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        this.id = UUID.randomUUID();
        this.credentialType = requireCredentialType(credentialType);
        this.providerName = normalizeProviderName(providerName);
        validateProviderForCredentialType(this.credentialType, this.providerName);
        this.accountIdentifier = validateAccountIdentifier(this.providerName, accountIdentifier);
        this.encryptedAccessToken = requireEncryptedToken(
                encryptedAccessToken, "Encrypted access token cannot be blank"
        );
        this.encryptedRefreshToken = optionalEncryptedToken(encryptedRefreshToken);
        this.expiresAt = expiresAt;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ApiCredential create(
            CredentialType credentialType,
            String providerName,
            String accountIdentifier,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            OffsetDateTime expiresAt
    ) {
        return new ApiCredential(
                credentialType, providerName, accountIdentifier,
                encryptedAccessToken, encryptedRefreshToken, expiresAt
        );
    }

    public void rotateTokens(
            String encryptedAccessToken,
            String encryptedRefreshToken,
            OffsetDateTime expiresAt
    ) {
        this.encryptedAccessToken = requireEncryptedToken(
                encryptedAccessToken, "Encrypted access token cannot be blank"
        );
        this.encryptedRefreshToken = optionalEncryptedToken(encryptedRefreshToken);
        this.expiresAt = expiresAt;
        this.updatedAt = OffsetDateTime.now();
    }

    public void updateAccountIdentifier(String accountIdentifier) {
        this.accountIdentifier = validateAccountIdentifier(providerName, accountIdentifier);
        this.updatedAt = OffsetDateTime.now();
    }

    public void changeActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID id() {
        return id;
    }

    public CredentialType credentialType() {
        return credentialType;
    }

    public String providerName() {
        return providerName;
    }

    public String accountIdentifier() {
        return accountIdentifier;
    }

    public String encryptedAccessToken() {
        return encryptedAccessToken;
    }

    public String encryptedRefreshToken() {
        return encryptedRefreshToken;
    }

    public boolean hasRefreshToken() {
        return encryptedRefreshToken != null;
    }

    public OffsetDateTime expiresAt() {
        return expiresAt;
    }

    public boolean active() {
        return active;
    }

    public boolean expiredAt(OffsetDateTime time) {
        return expiresAt != null && !expiresAt.isAfter(time);
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    private static String normalizeProviderName(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            throw new DomainException("Provider name cannot be blank");
        }
        return providerName.trim().toLowerCase(Locale.ROOT);
    }

    private static CredentialType requireCredentialType(CredentialType credentialType) {
        if (credentialType == null) {
            throw new DomainException("Credential type cannot be null");
        }
        return credentialType;
    }

    private static void validateProviderForCredentialType(
            CredentialType credentialType,
            String providerName
    ) {
        boolean supported = switch (credentialType) {
            case AI_PROVIDER -> providerName.equals("openai")
                    || providerName.equals("anthropic")
                    || providerName.equals("gemini")
                    || providerName.equals("deepseek")
                    || providerName.equals("qwen");
            case SOCIAL_PLATFORM -> providerName.equals("linkedin")
                    || providerName.equals("instagram")
                    || providerName.equals("twitter");
        };
        if (!supported) {
            throw new DomainException(
                    "Provider " + providerName + " is not supported for credential type " + credentialType
            );
        }
    }

    private static String requireEncryptedToken(String token, String message) {
        if (token == null || token.isBlank()) {
            throw new DomainException(message);
        }
        return token;
    }

    private static String optionalEncryptedToken(String token) {
        if (token == null) {
            return null;
        }
        if (token.isBlank()) {
            throw new DomainException("Encrypted refresh token cannot be blank");
        }
        return token;
    }

    private static String validateAccountIdentifier(String providerName, String accountIdentifier) {
        boolean required = providerName.equals("linkedin") || providerName.equals("instagram");
        if (accountIdentifier == null) {
            if (required) {
                throw new DomainException("Account identifier is required for provider " + providerName);
            }
            return null;
        }
        if (accountIdentifier.isBlank()) {
            throw new DomainException("Account identifier cannot be blank");
        }
        return accountIdentifier.trim();
    }
}
