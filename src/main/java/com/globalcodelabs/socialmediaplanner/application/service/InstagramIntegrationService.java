package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.application.command.ConnectSocialCredentialCommand;
import com.globalcodelabs.socialmediaplanner.common.exception.OAuthConnectionException;
import com.globalcodelabs.socialmediaplanner.domain.model.ApiCredential;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.OAuthStateStore;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.instagram.InstagramOAuthClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.instagram.InstagramOAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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
        SocialIntegrationSupport.requireCode(code, "Instagram");
        stateStore.consume(PROVIDER, state);
        InstagramOAuthClient.ShortTokenResponse shortToken;
        InstagramOAuthClient.LongTokenResponse token;
        try {
            shortToken = client.exchange(code);
            token = client.exchangeLongLived(shortToken.accessToken());
        } catch (RestClientException exception) {
            throw SocialIntegrationSupport.tokenExchangeException("Instagram", exception);
        }
        InstagramOAuthClient.UserInfo user;
        try {
            user = client.userInfo(token.accessToken());
        } catch (RestClientException exception) {
            throw SocialIntegrationSupport.accountLookupException("Instagram", exception);
        }
        String accountId = shortToken.userId();
        if (!accountId.equals(user.accountId())) {
            throw new OAuthConnectionException(
                    OAuthConnectionException.ACCOUNT_LOOKUP_FAILED,
                    "Instagram token hesabı ile doğrulanan hesap eşleşmiyor"
            );
        }
        ApiCredential credential = credentialService.connectSocialAccount(new ConnectSocialCredentialCommand(
                PROVIDER,
                accountId,
                displayName(user),
                token.accessToken(),
                null,
                token.expiresIn() == null ? null : OffsetDateTime.now().plusSeconds(token.expiresIn()),
                null,
                permissions(shortToken.permissions())
        ));
        return new ConnectionResult(credential.id().toString(), accountId, user.username(), user.accountType());
    }

    public ConnectionResult validate() {
        ResolvedApiCredential credential = credentialRefreshService.resolveValid(PROVIDER);
        InstagramOAuthClient.UserInfo user;
        try {
            user = client.userInfo(credential.accessToken());
        } catch (RestClientException exception) {
            throw SocialIntegrationSupport.accountLookupException("Instagram", exception);
        }
        if (!user.accountId().equals(credential.accountIdentifier())) {
            throw new OAuthConnectionException(
                    OAuthConnectionException.ACCOUNT_LOOKUP_FAILED,
                    "Doğrulanan Instagram hesabı kayıtlı hesapla eşleşmiyor"
            );
        }
        return new ConnectionResult(credential.credentialId().toString(), user.accountId(), user.username(), user.accountType());
    }

    public String frontendRedirectUri(String status, String errorCode) {
        return SocialIntegrationSupport.frontendRedirectUri(
                properties.requireFrontendRedirectUri(),
                "instagramConnection",
                status,
                errorCode
        );
    }

    private Set<String> permissions(String permissions) {
        return permissions == null || permissions.isBlank()
                ? properties.scopeSet()
                : Arrays.stream(permissions.trim().split("[,\\s]+"))
                        .filter(permission -> !permission.isBlank())
                        .collect(Collectors.toUnmodifiableSet());
    }

    private static String displayName(InstagramOAuthClient.UserInfo user) {
        return user.username() == null || user.username().isBlank() ? user.name() : "@" + user.username();
    }

    public record ConnectionResult(String credentialId, String accountId, String username, String accountType) {}
}
