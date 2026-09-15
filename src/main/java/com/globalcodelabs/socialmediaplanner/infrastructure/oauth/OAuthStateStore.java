package com.globalcodelabs.socialmediaplanner.infrastructure.oauth;

import com.globalcodelabs.socialmediaplanner.common.exception.OAuthConnectionException;
import com.globalcodelabs.socialmediaplanner.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OAuthStateStore {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final Map<String, PendingState> states = new ConcurrentHashMap<>();

    public String create(String provider, Duration ttl) {
        removeExpired();
        String state = randomValue();
        states.put(key(provider, state), new PendingState(Instant.now().plus(ttl)));
        return state;
    }

    public void consume(String provider, String state) {
        if (state == null || state.isBlank()) {
            throw new OAuthConnectionException(
                    ErrorCode.OAUTH_STATE_INVALID,
                    "OAuth bağlantı oturumu geçersiz veya süresi doldu"
            );
        }
        PendingState pending = states.remove(key(provider, state));
        if (pending == null || pending.expiresAt().isBefore(Instant.now())) {
            throw new OAuthConnectionException(
                    ErrorCode.OAUTH_STATE_INVALID,
                    "OAuth bağlantı oturumu geçersiz veya süresi doldu"
            );
        }
    }

    private void removeExpired() {
        Instant now = Instant.now();
        states.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private static String randomValue() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String key(String provider, String state) {
        return provider + ":" + state;
    }

    private record PendingState(Instant expiresAt) {
    }
}
