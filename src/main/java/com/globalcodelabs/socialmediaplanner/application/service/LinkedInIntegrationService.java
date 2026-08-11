package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.application.command.ConnectSocialCredentialCommand;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialService;
import com.globalcodelabs.socialmediaplanner.application.service.ResolvedApiCredential;
import com.globalcodelabs.socialmediaplanner.common.exception.OAuthConnectionException;
import com.globalcodelabs.socialmediaplanner.domain.model.ApiCredential;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.OAuthStateStore;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.linkedin.LinkedInOAuthClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.linkedin.LinkedInOAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.OffsetDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LinkedInIntegrationService {
    private static final String PROVIDER = "linkedin";
    private final LinkedInOAuthProperties properties;
    private final LinkedInOAuthClient client;
    private final OAuthStateStore stateStore;
    private final ApiCredentialService credentialService;
    private final SocialCredentialRefreshService credentialRefreshService;

    public String authorizationUri() {
        String state = stateStore.create(PROVIDER, properties.getStateTtl());
        return UriComponentsBuilder.fromUriString(properties.requireAuthorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.requireClientId())
                .queryParam("redirect_uri", properties.requireRedirectUri())
                .queryParam("state", state)
                .queryParam("scope", String.join(" ", properties.scopeSet()))
                .build().encode().toUriString();
    }

    public ConnectionResult complete(String code, String state) {
        requireCode(code);
        stateStore.consume(PROVIDER, state);
        LinkedInOAuthClient.TokenResponse token;
        try {
            token = client.exchange(code);
        } catch (RestClientResponseException exception) {
            throw tokenExchangeException(exception);
        } catch (RestClientException exception) {
            throw new OAuthConnectionException(
                    OAuthConnectionException.TOKEN_EXCHANGE_FAILED,
                    "LinkedIn erişim anahtarı alınamadı",
                    exception
            );
        }
        LinkedInOAuthClient.UserInfo user;
        try {
            user = client.userInfo(token.accessToken());
        } catch (RestClientException exception) {
            throw accountLookupException(exception);
        }
        OffsetDateTime now = OffsetDateTime.now();
        ApiCredential credential = credentialService.connectSocialAccount(new ConnectSocialCredentialCommand(
                PROVIDER,
                "urn:li:person:" + user.sub(),
                user.name(),
                token.accessToken(),
                token.refreshToken(),
                token.expiresIn() == null ? null : now.plusSeconds(token.expiresIn()),
                token.refreshTokenExpiresIn() == null ? null : now.plusSeconds(token.refreshTokenExpiresIn()),
                scopes(token.scope())
        ));
        return new ConnectionResult(credential.id().toString(), credential.accountIdentifier(), user.name());
    }

    public ConnectionResult validate() {
        ResolvedApiCredential credential = credentialRefreshService.resolveValid(PROVIDER);
        LinkedInOAuthClient.UserInfo user;
        try {
            user = client.userInfo(credential.accessToken());
        } catch (RestClientException exception) {
            throw accountLookupException(exception);
        }
        String accountId = "urn:li:person:" + user.sub();
        if (!accountId.equals(credential.accountIdentifier())) {
            throw new OAuthConnectionException(
                    OAuthConnectionException.ACCOUNT_LOOKUP_FAILED,
                    "Doğrulanan LinkedIn hesabı kayıtlı hesapla eşleşmiyor"
            );
        }
        return new ConnectionResult(credential.credentialId().toString(), accountId, user.name());
    }

    public String frontendRedirectUri(String status, String errorCode) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.requireFrontendRedirectUri())
                .queryParam("linkedinConnection", status);
        if (errorCode != null) builder.queryParam("errorCode", errorCode);
        return builder.build().encode().toUriString();
    }

    private static void requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new OAuthConnectionException(
                    OAuthConnectionException.TOKEN_EXCHANGE_FAILED,
                    "LinkedIn hesap bağlantısı tamamlanamadı"
            );
        }
    }

    private static OAuthConnectionException tokenExchangeException(RestClientResponseException exception) {
        String errorCode = exception.getStatusCode().value() == 401
                ? OAuthConnectionException.CONFIGURATION_ERROR
                : OAuthConnectionException.TOKEN_EXCHANGE_FAILED;
        String message = errorCode.equals(OAuthConnectionException.CONFIGURATION_ERROR)
                ? "LinkedIn uygulama yapılandırması geçersiz"
                : "LinkedIn erişim anahtarı alınamadı";
        return new OAuthConnectionException(errorCode, message, exception);
    }

    private static OAuthConnectionException accountLookupException(RestClientException exception) {
        boolean permissionMissing = exception instanceof RestClientResponseException response
                && (response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403);
        return new OAuthConnectionException(
                permissionMissing
                        ? OAuthConnectionException.PERMISSION_MISSING
                        : OAuthConnectionException.ACCOUNT_LOOKUP_FAILED,
                permissionMissing
                        ? "LinkedIn hesabı için gerekli izinler bulunmuyor"
                        : "LinkedIn hesap bilgileri alınamadı",
                exception
        );
    }

    private Set<String> scopes(String scope) {
        return scope == null || scope.isBlank() ? properties.scopeSet() : Set.of(scope.trim().split("\\s+"));
    }

    public record ConnectionResult(String credentialId, String accountId, String displayName) {}
}
