package com.globalcodelabs.socialmediaplanner.infrastructure.oauth.instagram;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.OAuthClientSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.function.Supplier;

@Component
@Slf4j
public class InstagramOAuthClient {
    private static final String PROVIDER = "instagram";

    private final InstagramOAuthProperties properties;
    private final RestClient restClient;

    public InstagramOAuthClient(InstagramOAuthProperties properties) {
        this.properties = properties;
        this.restClient = OAuthClientSupport.restClient(
                properties.getConnectTimeout(),
                properties.getReadTimeout()
        );
    }

    public ShortTokenResponse exchange(String code) {
        return execute("exchange-authorization-code", () -> {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", properties.requireClientId());
            form.add("client_secret", properties.requireClientSecret());
            form.add("grant_type", "authorization_code");
            form.add("redirect_uri", properties.requireRedirectUri());
            form.add("code", code);
            ShortTokenEnvelope response = restClient.post().uri(properties.requireTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(ShortTokenEnvelope.class);
            ShortTokenResponse token = response == null ? null : response.resolved();
            if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
                throw new IllegalStateException("Instagram token endpoint did not return an access token");
            }
            if (token.userId() == null || token.userId().isBlank()) {
                throw new IllegalStateException("Instagram token endpoint did not return a user id");
            }
            return token;
        });
    }

    public LongTokenResponse exchangeLongLived(String shortToken) {
        return execute("exchange-long-lived-token", () -> {
            String uri = UriComponentsBuilder.fromUriString(properties.requireLongLivedTokenUri())
                    .queryParam("grant_type", "ig_exchange_token")
                    .queryParam("client_secret", properties.requireClientSecret())
                    .queryParam("access_token", shortToken).build().encode().toUriString();
            LongTokenResponse response = restClient.get().uri(uri).retrieve().body(LongTokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new IllegalStateException("Instagram did not return a long-lived access token");
            }
            return response;
        });
    }

    public LongTokenResponse refreshLongLivedToken(String accessToken) {
        return execute("refresh-long-lived-token", () -> {
            String uri = UriComponentsBuilder.fromUriString(properties.requireRefreshTokenUri())
                    .queryParam("grant_type", "ig_refresh_token")
                    .queryParam("access_token", accessToken).build().encode().toUriString();
            LongTokenResponse response = restClient.get().uri(uri).retrieve().body(LongTokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new IllegalStateException("Instagram token refresh did not return an access token");
            }
            return response;
        });
    }

    public UserInfo userInfo(String accessToken) {
        return execute("get-user-info", () -> {
            UserInfoEnvelope response = restClient.get().uri(properties.requireUserInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().body(UserInfoEnvelope.class);
            UserInfo user = response == null ? null : response.resolved();
            if (user == null || user.accountId() == null || user.accountId().isBlank()) {
                throw new IllegalStateException("Instagram user info endpoint did not return an account id");
            }
            return user;
        });
    }

    private <T> T execute(String operation, Supplier<T> call) {
        return OAuthClientSupport.execute(PROVIDER, operation, call, log);
    }

    private record ShortTokenEnvelope(
            List<ShortTokenResponse> data,
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("user_id") String userId,
            String permissions
    ) {
        private ShortTokenResponse resolved() {
            if (data != null && !data.isEmpty()) {
                return data.getFirst();
            }
            return new ShortTokenResponse(accessToken, userId, permissions);
        }
    }

    public record ShortTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("user_id") String userId,
            String permissions
    ) {}
    public record LongTokenResponse(@JsonProperty("access_token") String accessToken,
                                    @JsonProperty("token_type") String tokenType,
                                    @JsonProperty("expires_in") Long expiresIn) {}
    private record UserInfoEnvelope(
            List<UserInfo> data,
            String id,
            @JsonProperty("user_id") String userId,
            String username,
            String name,
            @JsonProperty("account_type") String accountType
    ) {
        private UserInfo resolved() {
            if (data != null && !data.isEmpty()) {
                return data.getFirst();
            }
            return new UserInfo(id, userId, username, name, accountType);
        }
    }

    public record UserInfo(String id, @JsonProperty("user_id") String userId, String username, String name,
                           @JsonProperty("account_type") String accountType) {
        public String accountId() { return userId != null && !userId.isBlank() ? userId : id; }
    }
}
