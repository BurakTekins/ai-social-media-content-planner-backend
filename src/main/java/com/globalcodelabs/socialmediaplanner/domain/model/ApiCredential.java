package com.globalcodelabs.socialmediaplanner.domain.model;

import com.globalcodelabs.socialmediaplanner.common.exception.DomainException;
import com.globalcodelabs.socialmediaplanner.domain.enums.CredentialType;
import com.globalcodelabs.socialmediaplanner.domain.enums.CredentialValidationStatus;
import com.globalcodelabs.socialmediaplanner.domain.policy.DomainValidation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "api_credential")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Accessors(fluent = true)
public class ApiCredential {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false, length = 30, updatable = false)
    private CredentialType credentialType;

    @Column(name = "provider_name", nullable = false, updatable = false)
    private String providerName;

    @Column(name = "owner_id")
    @Getter(AccessLevel.NONE)
    private UUID ownerId;

    @Column(name = "account_identifier", columnDefinition = "TEXT")
    private String accountIdentifier;

    @Column(name = "account_display_name")
    private String accountDisplayName;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "granted_scopes", nullable = false, columnDefinition = "TEXT[]")
    @Getter(AccessLevel.NONE)
    private String[] grantedScopes;

    @Column(name = "encrypted_access_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedAccessToken;

    @Column(name = "encrypted_refresh_token", columnDefinition = "TEXT")
    private String encryptedRefreshToken;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "refresh_token_expires_at")
    private OffsetDateTime refreshTokenExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 20)
    private CredentialValidationStatus validationStatus;

    @Column(name = "last_validated_at")
    private OffsetDateTime lastValidatedAt;

    @Column(name = "validation_error", columnDefinition = "TEXT")
    private String validationError;

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
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.id = UUID.randomUUID();
        this.credentialType = requireCredentialType(credentialType);
        this.providerName = normalizeProviderName(providerName);
        validateProviderForCredentialType(this.credentialType, this.providerName);
        this.accountIdentifier = validateAccountIdentifier(this.providerName, accountIdentifier);
        this.encryptedAccessToken = requireEncryptedToken(
                encryptedAccessToken, "Encrypted access token cannot be blank"
        );
        this.encryptedRefreshToken = optionalEncryptedToken(encryptedRefreshToken);
        this.expiresAt = toUtc(expiresAt);
        this.grantedScopes = new String[0];
        this.validationStatus = CredentialValidationStatus.UNVERIFIED;
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
        this.expiresAt = toUtc(expiresAt);
        this.validationStatus = CredentialValidationStatus.UNVERIFIED;
        this.lastValidatedAt = null;
        this.validationError = null;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void refreshTokens(
            String encryptedAccessToken,
            String encryptedRefreshToken,
            OffsetDateTime expiresAt,
            OffsetDateTime refreshTokenExpiresAt
    ) {
        this.encryptedAccessToken = requireEncryptedToken(
                encryptedAccessToken, "Encrypted access token cannot be blank"
        );
        if (encryptedRefreshToken != null) {
            this.encryptedRefreshToken = optionalEncryptedToken(encryptedRefreshToken);
        }
        this.expiresAt = toUtc(expiresAt);
        if (refreshTokenExpiresAt != null) {
            this.refreshTokenExpiresAt = toUtc(refreshTokenExpiresAt);
        }
        this.validationStatus = CredentialValidationStatus.VALID;
        this.validationError = null;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void updateAccountIdentifier(String accountIdentifier) {
        this.accountIdentifier = validateAccountIdentifier(providerName, accountIdentifier);
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markValidated(
            String accountDisplayName,
            Set<String> grantedScopes,
            OffsetDateTime refreshTokenExpiresAt
    ) {
        this.accountDisplayName = optionalText(accountDisplayName);
        this.grantedScopes = normalizeScopes(grantedScopes);
        this.refreshTokenExpiresAt = toUtc(refreshTokenExpiresAt);
        this.validationStatus = CredentialValidationStatus.VALID;
        this.lastValidatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.validationError = null;
        this.active = true;
        this.updatedAt = this.lastValidatedAt;
    }

    public void markValidationFailed(String validationError) {
        this.validationStatus = CredentialValidationStatus.INVALID;
        this.lastValidatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.validationError = DomainValidation.requireText(
                validationError,
                "Validation error cannot be blank"
        );
        this.updatedAt = this.lastValidatedAt;
    }

    public void changeActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Set<String> grantedScopes() {
        return Set.copyOf(Arrays.asList(grantedScopes));
    }

    public boolean hasRefreshToken() {
        return encryptedRefreshToken != null;
    }

    public boolean expiredAt(OffsetDateTime time) {
        return expiresAt != null && !expiresAt.isAfter(time);
    }

    private static OffsetDateTime toUtc(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC);
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
        String normalized = accountIdentifier.trim();
        if (providerName.equals("twitter") && !normalized.matches("\\d+")) {
            throw new DomainException("X account identifier must be a numeric user id");
        }
        return normalized;
    }

    private static String[] normalizeScopes(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return new String[0];
        }
        return scopes.stream()
                .map(scope -> DomainValidation.requireText(scope, "Granted scope cannot be blank"))
                .distinct()
                .sorted()
                .toArray(String[]::new);
    }

    private static String optionalText(String value) {
        return value == null
                ? null
                : DomainValidation.requireText(value, "Text value cannot be blank");
    }
}
