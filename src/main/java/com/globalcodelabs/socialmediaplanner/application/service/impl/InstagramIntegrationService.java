package com.globalcodelabs.socialmediaplanner.application.service.impl;

import com.globalcodelabs.socialmediaplanner.application.command.ConnectSocialCredentialCommand;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialService;
import com.globalcodelabs.socialmediaplanner.application.service.ResolvedApiCredential;
import com.globalcodelabs.socialmediaplanner.common.exception.OAuthConnectionException;
import com.globalcodelabs.socialmediaplanner.domain.model.ApiCredential;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.OAuthStateStore;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.instagram.InstagramOAuthClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.instagram.InstagramOAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InstagramIntegrationService {
    private static final String PROVIDER = "instagram";
    private final InstagramOAuthProperties properties;
    private final InstagramOAuthClient client;
    private final OAuthStateStore stateStore;
    private final ApiCredentialService credentialService;
    private final SocialCredentialRefreshService credentialRefreshService;

    public String authorizationUri() {
        String state = stateStore.create(PROVIDER, properties.getStateTtl());
        return UriComponentsBuilder.fromUriString(properties.requireAuthorizationUri())
                .queryParam("enable_fb_login", "0")
                .queryParam("force_authentication", "1")
                .queryParam("client_id", properties.requireClientId())
                .queryParam("redirect_uri", properties.requireRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(",", properties.scopeSet()))
                .queryParam("state", state)
                .build().encode().toUriString();
    }

    public ConnectionResult complete(String code, String state) {
        requireCode(code);
        stateStore.consume(PROVIDER, state);
        InstagramOAuthClient.ShortTokenResponse shortToken;
        InstagramOAuthClient.LongTokenResponse token;
        try {
            shortToken = client.exchange(code);
            token = client.exchangeLongLived(shortToken.accessToken());
        } catch (RestClientResponseException exception) {
            throw tokenExchangeException(exception);
        } catch (RestClientException exception) {
            throw new OAuthConnectionException(
                    OAuthConnectionException.TOKEN_EXCHANGE_FAILED,
                    "Instagram erişim anahtarı alınamadı",
                    exception
            );
        }
        InstagramOAuthClient.UserInfo user;
        try {
            user = client.userInfo(token.accessToken());
        } catch (RestClientException exception) {
            throw accountLookupException(exception);
        }
        ApiCredential credential = credentialService.connectSocialAccount(new ConnectSocialCredentialCommand(
                PROVIDER,
                user.resolvedId(),
                displayName(user),
                token.accessToken(),
                null,
                token.expiresIn() == null ? null : OffsetDateTime.now().plusSeconds(token.expiresIn()),
                null,
                permissions(shortToken.permissions())
        ));
        return new ConnectionResult(credential.id().toString(), user.resolvedId(), user.username(), user.accountType());
    }

    public ConnectionResult validate() {
        ResolvedApiCredential credential = credentialRefreshService.resolveValid(PROVIDER);
        InstagramOAuthClient.UserInfo user;
        try {
            user = client.userInfo(credential.accessToken());
        } catch (RestClientException exception) {
            throw accountLookupException(exception);
        }
        if (!user.resolvedId().equals(credential.accountIdentifier())) {
            throw new OAuthConnectionException(
                    OAuthConnectionException.ACCOUNT_LOOKUP_FAILED,
                    "Doğrulanan Instagram hesabı kayıtlı hesapla eşleşmiyor"
            );
        }
        return new ConnectionResult(credential.credentialId().toString(), user.resolvedId(), user.username(), user.accountType());
    }

    public String frontendRedirectUri(String status, String errorCode) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.requireFrontendRedirectUri())
                .queryParam("instagramConnection", status);
        if (errorCode != null) builder.queryParam("errorCode", errorCode);
        return builder.build().encode().toUriString();
    }

    private static void requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new OAuthConnectionException(
                    OAuthConnectionException.TOKEN_EXCHANGE_FAILED,
                    "Instagram hesap bağlantısı tamamlanamadı"
            );
        }
    }

    private static OAuthConnectionException tokenExchangeException(RestClientResponseException exception) {
        String errorCode = exception.getStatusCode().value() == 401
                ? OAuthConnectionException.CONFIGURATION_ERROR
                : OAuthConnectionException.TOKEN_EXCHANGE_FAILED;
        String message = errorCode.equals(OAuthConnectionException.CONFIGURATION_ERROR)
                ? "Instagram uygulama yapılandırması geçersiz"
                : "Instagram erişim anahtarı alınamadı";
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
                        ? "Instagram hesabı için gerekli izinler bulunmuyor"
                        : "Instagram hesap bilgileri alınamadı",
                exception
        );
    }

    private Set<String> permissions(String[] permissions) {
        return permissions == null || permissions.length == 0
                ? properties.scopeSet() : Set.copyOf(Arrays.asList(permissions));
    }

    private static String displayName(InstagramOAuthClient.UserInfo user) {
        return user.username() == null || user.username().isBlank() ? user.name() : "@" + user.username();
    }

    public record ConnectionResult(String credentialId, String accountId, String username, String accountType) {}
}
