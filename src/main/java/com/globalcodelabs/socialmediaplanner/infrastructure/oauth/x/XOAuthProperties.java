package com.globalcodelabs.socialmediaplanner.infrastructure.oauth.x;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@ConfigurationProperties(prefix = "platform-oauth.x")
public class XOAuthProperties {

    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String frontendRedirectUri;
    private String authorizationUri;
    private String tokenUri;
    private String userInfoUri;
    private String scopes;
    private Duration stateTtl = Duration.ofMinutes(10);
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(30);

    public String requireClientId() {
        return require(clientId, "X OAuth client ID is not configured");
    }

    public String requireClientSecret() {
        return require(clientSecret, "X OAuth client secret is not configured");
    }

    public String requireRedirectUri() {
        return require(redirectUri, "X OAuth redirect URI is not configured");
    }

    public String requireFrontendRedirectUri() {
        return require(frontendRedirectUri, "X frontend redirect URI is not configured");
    }

    public String requireAuthorizationUri() {
        return require(authorizationUri, "X OAuth authorization URI is not configured");
    }

    public String requireTokenUri() {
        return require(tokenUri, "X OAuth token URI is not configured");
    }

    public String requireUserInfoUri() {
        return require(userInfoUri, "X user info URI is not configured");
    }

    public Set<String> scopeSet() {
        return Arrays.stream(require(scopes, "X OAuth scopes are not configured").split("\\s+"))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }
}
