package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.application.command.ConnectSocialCredentialCommand;
import com.globalcodelabs.socialmediaplanner.common.exception.OAuthConnectionException;
import com.globalcodelabs.socialmediaplanner.domain.model.ApiCredential;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.x.XOAuthClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.x.XOAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class XIntegrationService {

    private static final String PROVIDER_NAME = "twitter";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final XOAuthProperties properties;
    private final XOAuthClient xOAuthClient;
    private final ApiCredentialService apiCredentialService;
    private final SocialCredentialRefreshService credentialRefreshService;
    private final Map<String, PendingAuthorization> pendingAuthorizations = new ConcurrentHashMap<>();

    public String authorizationUri() {
        removeExpiredStates();
        String state = randomUrlSafeValue(32);
        String codeVerifier = randomUrlSafeValue(64);
        String codeChallenge = sha256UrlSafe(codeVerifier);
        pendingAuthorizations.put(
                state,
                new PendingAuthorization(
                        codeVerifier,
                        Instant.now().plus(properties.getStateTtl())
                )
        );
        return UriComponentsBuilder.fromUriString(properties.requireAuthorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.requireClientId())
                .queryParam("redirect_uri", properties.requireRedirectUri())
                .queryParam("scope", String.join(" ", properties.scopeSet()))
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUriString();
    }

    public ConnectionResult completeAuthorization(String code, String state) {
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new OAuthConnectionException(
                    state == null || state.isBlank()
                            ? OAuthConnectionException.STATE_INVALID
                            : OAuthConnectionException.TOKEN_EXCHANGE_FAILED,
                    "X hesap bağlantısı tamamlanamadı"
            );
        }
        PendingAuthorization pending = pendingAuthorizations.remove(state);
        if (pending == null || pending.expiresAt().isBefore(Instant.now())) {
            throw new OAuthConnectionException(
                    OAuthConnectionException.STATE_INVALID,
                    "X bağlantı oturumu geçersiz veya süresi doldu"
            );
        }

        XOAuthClient.TokenResponse token;
        try {
            token = xOAuthClient.exchangeAuthorizationCode(
                    code, pending.codeVerifier()
            );
        } catch (RestClientException exception) {
            throw SocialIntegrationSupport.tokenExchangeException("X", exception);
        }

        XOAuthClient.UserData user;
        try {
            user = xOAuthClient.getAuthenticatedUser(token.accessToken());
        } catch (RestClientException exception) {
            throw SocialIntegrationSupport.accountLookupException("X", exception);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = token.expiresIn() == null
                ? null
                : now.plusSeconds(token.expiresIn());
        Set<String> grantedScopes = scopes(token.scope());
        ApiCredential credential = apiCredentialService.connectSocialAccount(
                new ConnectSocialCredentialCommand(
                        PROVIDER_NAME,
                        user.id(),
                        displayName(user),
                        token.accessToken(),
                        token.refreshToken(),
                        expiresAt,
                        null,
                        grantedScopes
                )
        );
        return new ConnectionResult(
                credential.id().toString(), user.id(), user.username(), user.name()
        );
    }

    public ConnectionResult validateConnectedAccount() {
        ResolvedApiCredential credential = credentialRefreshService.resolveValid(PROVIDER_NAME);
        try {
            XOAuthClient.UserData user = xOAuthClient.getAuthenticatedUser(credential.accessToken());
            if (credential.accountIdentifier() != null
                    && !credential.accountIdentifier().equals(user.id())) {
                throw new OAuthConnectionException(
                        OAuthConnectionException.ACCOUNT_LOOKUP_FAILED,
                        "Doğrulanan X hesabı kayıtlı hesapla eşleşmiyor"
                );
            }
            return new ConnectionResult(
                    credential.credentialId().toString(), user.id(), user.username(), user.name()
            );
        } catch (OAuthConnectionException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw SocialIntegrationSupport.accountLookupException("X", exception);
        }
    }

    public String frontendRedirectUri(String status, String errorCode) {
        return SocialIntegrationSupport.frontendRedirectUri(
                properties.requireFrontendRedirectUri(),
                "xConnection",
                status,
                errorCode
        );
    }

    private void removeExpiredStates() {
        Instant now = Instant.now();
        pendingAuthorizations.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private static Set<String> scopes(String scope) {
        if (scope == null || scope.isBlank()) {
            return Set.of();
        }
        return Set.of(scope.trim().split("\\s+"));
    }

    private static String displayName(XOAuthClient.UserData user) {
        if (user.username() == null || user.username().isBlank()) {
            return user.name();
        }
        return "@" + user.username();
    }

    private static String randomUrlSafeValue(int byteCount) {
        byte[] bytes = new byte[byteCount];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256UrlSafe(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record PendingAuthorization(String codeVerifier, Instant expiresAt) {
    }

    public record ConnectionResult(
            String credentialId,
            String accountId,
            String username,
            String displayName
    ) {
    }
}
