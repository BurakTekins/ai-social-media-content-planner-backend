package com.globalcodelabs.socialmediaplanner.infrastructure.oauth.instagram;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@ConfigurationProperties(prefix = "platform-oauth.instagram")
public class InstagramOAuthProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String frontendRedirectUri;
    private String authorizationUri;
    private String tokenUri;
    private String longLivedTokenUri;
    private String refreshTokenUri;
    private String userInfoUri;
    private String scopes;
    private Duration stateTtl = Duration.ofMinutes(10);
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(30);

    public String requireClientId() { return require(clientId, "Instagram client ID is not configured"); }
    public String requireClientSecret() { return require(clientSecret, "Instagram client secret is not configured"); }
    public String requireRedirectUri() { return require(redirectUri, "Instagram redirect URI is not configured"); }
    public String requireFrontendRedirectUri() { return require(frontendRedirectUri, "Instagram frontend redirect URI is not configured"); }
    public String requireAuthorizationUri() { return require(authorizationUri, "Instagram authorization URI is not configured"); }
    public String requireTokenUri() { return require(tokenUri, "Instagram token URI is not configured"); }
    public String requireLongLivedTokenUri() { return require(longLivedTokenUri, "Instagram long-lived token URI is not configured"); }
    public String requireRefreshTokenUri() { return require(refreshTokenUri, "Instagram refresh token URI is not configured"); }
    public String requireUserInfoUri() { return require(userInfoUri, "Instagram user info URI is not configured"); }
    public Set<String> scopeSet() {
        return Arrays.stream(require(scopes, "Instagram scopes are not configured").split("\\s+"))
                .filter(value -> !value.isBlank()).collect(Collectors.toUnmodifiableSet());
    }
    private static String require(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalStateException(message);
        return value.trim();
    }
}
