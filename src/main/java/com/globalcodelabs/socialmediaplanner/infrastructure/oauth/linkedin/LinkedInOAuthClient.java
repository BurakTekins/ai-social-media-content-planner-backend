package com.globalcodelabs.socialmediaplanner.infrastructure.oauth.linkedin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.OAuthClientSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

@Component
@Slf4j
public class LinkedInOAuthClient {
    private static final String PROVIDER = "linkedin";

    private final LinkedInOAuthProperties properties;
    private final RestClient restClient;

    public LinkedInOAuthClient(LinkedInOAuthProperties properties) {
        this.properties = properties;
        this.restClient = OAuthClientSupport.restClient(
                properties.getConnectTimeout(),
                properties.getReadTimeout()
        );
    }

    public TokenResponse exchange(String code) {
        return execute("exchange-authorization-code", () -> {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("code", code);
            form.add("client_id", properties.requireClientId());
            form.add("client_secret", properties.requireClientSecret());
            form.add("redirect_uri", properties.requireRedirectUri());
            TokenResponse response = restClient.post().uri(properties.requireTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(TokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new IllegalStateException("LinkedIn token endpoint did not return an access token");
            }
            return response;
        });
    }

    public TokenResponse refreshAccessToken(String refreshToken) {
        return execute("refresh-access-token", () -> {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "refresh_token");
            form.add("refresh_token", refreshToken);
            form.add("client_id", properties.requireClientId());
            form.add("client_secret", properties.requireClientSecret());
            TokenResponse response = restClient.post().uri(properties.requireTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(TokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new IllegalStateException("LinkedIn token refresh did not return an access token");
            }
            return response;
        });
    }

    public UserInfo userInfo(String accessToken) {
        return execute("get-user-info", () -> {
            UserInfo response = restClient.get().uri(properties.requireUserInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().body(UserInfo.class);
            if (response == null || response.sub() == null || response.sub().isBlank()) {
                throw new IllegalStateException("LinkedIn user info endpoint did not return a subject");
            }
            return response;
        });
    }

    private <T> T execute(String operation, Supplier<T> call) {
        return OAuthClientSupport.execute(PROVIDER, operation, call, log);
    }

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("refresh_token_expires_in") Long refreshTokenExpiresIn,
            String scope
    ) {}
    public record UserInfo(String sub, String name) {}
}
