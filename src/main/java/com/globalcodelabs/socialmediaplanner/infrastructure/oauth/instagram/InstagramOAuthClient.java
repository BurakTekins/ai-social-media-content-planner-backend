package com.globalcodelabs.socialmediaplanner.infrastructure.oauth.instagram;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.http.HttpClient;

@Component
public class InstagramOAuthClient {
    private final InstagramOAuthProperties properties;
    private final RestClient restClient;

    public InstagramOAuthClient(InstagramOAuthProperties properties) {
        this.properties = properties;
        HttpClient client = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public ShortTokenResponse exchange(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.requireClientId());
        form.add("client_secret", properties.requireClientSecret());
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", properties.requireRedirectUri());
        form.add("code", code);
        ShortTokenResponse response = restClient.post().uri(properties.requireTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(ShortTokenResponse.class);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("Instagram token endpoint did not return an access token");
        }
        return response;
    }

    public LongTokenResponse exchangeLongLived(String shortToken) {
        String uri = UriComponentsBuilder.fromUriString(properties.requireLongLivedTokenUri())
                .queryParam("grant_type", "ig_exchange_token")
                .queryParam("client_secret", properties.requireClientSecret())
                .queryParam("access_token", shortToken).build().encode().toUriString();
        LongTokenResponse response = restClient.get().uri(uri).retrieve().body(LongTokenResponse.class);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("Instagram did not return a long-lived access token");
        }
        return response;
    }

    public LongTokenResponse refreshLongLivedToken(String accessToken) {
        String uri = UriComponentsBuilder.fromUriString(properties.requireRefreshTokenUri())
                .queryParam("grant_type", "ig_refresh_token")
                .queryParam("access_token", accessToken).build().encode().toUriString();
        LongTokenResponse response = restClient.get().uri(uri).retrieve().body(LongTokenResponse.class);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("Instagram token refresh did not return an access token");
        }
        return response;
    }

    public UserInfo userInfo(String accessToken) {
        UserInfo response = restClient.get().uri(properties.requireUserInfoUri())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve().body(UserInfo.class);
        if (response == null || response.resolvedId() == null || response.resolvedId().isBlank()) {
            throw new IllegalStateException("Instagram user info endpoint did not return an account id");
        }
        return response;
    }

    public record ShortTokenResponse(@JsonProperty("access_token") String accessToken,
                                     @JsonProperty("user_id") String userId,
                                     String[] permissions) {}
    public record LongTokenResponse(@JsonProperty("access_token") String accessToken,
                                    @JsonProperty("token_type") String tokenType,
                                    @JsonProperty("expires_in") Long expiresIn) {}
    public record UserInfo(String id, @JsonProperty("user_id") String userId, String username, String name,
                           @JsonProperty("account_type") String accountType) {
        public String resolvedId() { return id != null && !id.isBlank() ? id : userId; }
    }
}
