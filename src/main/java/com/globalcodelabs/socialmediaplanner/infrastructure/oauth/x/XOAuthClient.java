package com.globalcodelabs.socialmediaplanner.infrastructure.oauth.x;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.globalcodelabs.socialmediaplanner.common.logging.MdcUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

@Slf4j
@Component
public class XOAuthClient {
    private static final String PROVIDER = "twitter";

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
        long startedAt = System.nanoTime();
        MdcUtil.putProvider(PROVIDER);
        try {
            log.info("OAuth provider call started operation={}", operation);
            T result = call.get();
            log.info("OAuth provider call completed operation={} durationMs={}",
                    operation, elapsedMilliseconds(startedAt));
            return result;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                log.warn("OAuth provider call rejected operation={} httpStatus={} durationMs={}",
                        operation, exception.getStatusCode().value(), elapsedMilliseconds(startedAt));
            } else {
                log.error("OAuth provider call failed operation={} httpStatus={} durationMs={}",
                        operation, exception.getStatusCode().value(),
                        elapsedMilliseconds(startedAt), exception);
            }
            throw exception;
        } catch (RuntimeException exception) {
            log.error("OAuth provider call failed operation={} errorType={} durationMs={}",
                    operation, exception.getClass().getSimpleName(),
                    elapsedMilliseconds(startedAt), exception);
            throw exception;
        } finally {
            MdcUtil.removeProvider();
        }
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
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
