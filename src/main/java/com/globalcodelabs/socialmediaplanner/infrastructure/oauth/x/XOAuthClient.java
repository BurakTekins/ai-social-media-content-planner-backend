package com.globalcodelabs.socialmediaplanner.infrastructure.oauth.x;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.OAuthClientSupport;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

@Slf4j
@Component
public class XOAuthClient {
    private static final String PROVIDER = Platform.TWITTER.providerName();

    private final XOAuthProperties properties;
    private final RestClient restClient;

    public XOAuthClient(XOAuthProperties properties) {
        this.properties = properties;
        this.restClient = OAuthClientSupport.restClient(
                properties.getConnectTimeout(),
                properties.getReadTimeout()
        );
    }

    public TokenResponse exchangeAuthorizationCode(String code, String codeVerifier) {
        return execute("exchange-authorization-code", () -> {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("code", code);
            form.add("redirect_uri", properties.requireRedirectUri());
            form.add("code_verifier", codeVerifier);
            TokenResponse response = restClient.post()
                    .uri(properties.requireTokenUri())
                    .headers(headers -> headers.setBasicAuth(
                            properties.requireClientId(),
                            properties.requireClientSecret(),
                            StandardCharsets.UTF_8
                    ))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new IllegalStateException("X token endpoint did not return an access token");
            }
            return response;
        });
    }

    public TokenResponse refreshAccessToken(String refreshToken) {
        return execute("refresh-access-token", () -> {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "refresh_token");
            form.add("refresh_token", refreshToken);
            TokenResponse response = restClient.post()
                    .uri(properties.requireTokenUri())
                    .headers(headers -> headers.setBasicAuth(
                            properties.requireClientId(),
                            properties.requireClientSecret(),
                            StandardCharsets.UTF_8
                    ))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new IllegalStateException("X token refresh did not return an access token");
            }
            return response;
        });
    }

    public UserData getAuthenticatedUser(String accessToken) {
        return execute("get-authenticated-user", () -> {
            UserResponse response = restClient.get()
                    .uri(properties.requireUserInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(UserResponse.class);
            if (response == null || response.data() == null
                    || response.data().id() == null || response.data().id().isBlank()) {
                throw new IllegalStateException("X user endpoint did not return an account id");
            }
            return response.data();
        });
    }

    private <T> T execute(String operation, Supplier<T> call) {
        return OAuthClientSupport.execute(PROVIDER, operation, call, log);
    }

    public record TokenResponse(
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("access_token") String accessToken,
            String scope,
            @JsonProperty("refresh_token") String refreshToken
    ) {
    }

    public record UserResponse(UserData data) {
    }

    public record UserData(String id, String name, String username) {
    }
}
