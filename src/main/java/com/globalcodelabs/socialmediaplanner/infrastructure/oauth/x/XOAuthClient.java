package com.globalcodelabs.socialmediaplanner.infrastructure.oauth.x;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class XOAuthClient {

    private final XOAuthProperties properties;
    private final RestClient restClient;

    public XOAuthClient(XOAuthProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public TokenResponse exchangeAuthorizationCode(String code, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.requireRedirectUri());
        form.add("code_verifier", codeVerifier);
        log.info("X OAuth call started operation=exchange-authorization-code provider=twitter");
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
        log.info("X OAuth call completed operation=exchange-authorization-code provider=twitter");
        return response;
    }

    public TokenResponse refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        log.info("X OAuth call started operation=refresh-access-token provider=twitter");
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
        log.info("X OAuth call completed operation=refresh-access-token provider=twitter");
        return response;
    }

    public UserData getAuthenticatedUser(String accessToken) {
        log.info("X API call started operation=get-authenticated-user provider=twitter");
        UserResponse response = restClient.get()
                .uri(properties.requireUserInfoUri())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(UserResponse.class);
        if (response == null || response.data() == null
                || response.data().id() == null || response.data().id().isBlank()) {
            throw new IllegalStateException("X user endpoint did not return an account id");
        }
        log.info("X API call completed operation=get-authenticated-user provider=twitter");
        return response.data();
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
