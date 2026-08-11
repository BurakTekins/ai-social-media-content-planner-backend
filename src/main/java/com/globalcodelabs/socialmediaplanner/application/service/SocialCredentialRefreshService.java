package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.application.command.RefreshApiCredentialCommand;
import com.globalcodelabs.socialmediaplanner.common.exception.OAuthConnectionException;
import com.globalcodelabs.socialmediaplanner.domain.model.ApiCredential;
import com.globalcodelabs.socialmediaplanner.domain.enums.CredentialType;
import com.globalcodelabs.socialmediaplanner.domain.repository.ApiCredentialRepository;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.OAuthRefreshProperties;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.instagram.InstagramOAuthClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.linkedin.LinkedInOAuthClient;
import com.globalcodelabs.socialmediaplanner.infrastructure.oauth.x.XOAuthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialCredentialRefreshService {

    private final ApiCredentialService credentialService;
    private final ApiCredentialRepository credentialRepository;
    private final XOAuthClient xOAuthClient;
    private final LinkedInOAuthClient linkedInOAuthClient;
    private final InstagramOAuthClient instagramOAuthClient;
    private final OAuthRefreshProperties properties;
    private final Map<String, Object> providerLocks = new ConcurrentHashMap<>();

    public ResolvedApiCredential resolveValid(String providerName) {
        String provider = normalize(providerName);
        synchronized (providerLocks.computeIfAbsent(provider, ignored -> new Object())) {
            ResolvedApiCredential credential = credentialService.resolveActiveIncludingExpired(
                    CredentialType.SOCIAL_PLATFORM,
                    provider
            );
            if (!refreshRequired(credential, provider, OffsetDateTime.now(ZoneOffset.UTC))) {
                return credential;
            }
            refresh(credential);
            return credentialService.resolveActive(CredentialType.SOCIAL_PLATFORM, provider);
        }
    }

    public int refreshExpiringCredentials() {
        List<ApiCredential> credentials = credentialRepository.findAllByCredentialTypeAndActiveTrue(
                CredentialType.SOCIAL_PLATFORM
        );
        int refreshedCount = 0;
        for (ApiCredential credential : credentials) {
            try {
                ResolvedApiCredential resolved = credentialService.resolveActiveIncludingExpired(
                        CredentialType.SOCIAL_PLATFORM,
                        credential.providerName()
                );
                if (!refreshRequired(resolved, credential.providerName(), OffsetDateTime.now(ZoneOffset.UTC))) {
                    continue;
                }
                resolveValid(credential.providerName());
                refreshedCount++;
            } catch (RuntimeException exception) {
                if (exception instanceof OAuthConnectionException) {
                    log.warn(
                            "Social credential refresh rejected provider={} errorType={}",
                            credential.providerName(),
                            exception.getClass().getSimpleName()
                    );
                } else {
                    log.error(
                            "Social credential refresh failed provider={} errorType={}",
                            credential.providerName(),
                            exception.getClass().getSimpleName(),
                            exception
                    );
                }
            }
        }
        return refreshedCount;
    }

    private boolean refreshRequired(
            ResolvedApiCredential credential,
            String provider,
            OffsetDateTime now
    ) {
        if (credential.expiresAt() == null) {
            return false;
        }
        if (provider.equals("linkedin") && !hasRefreshToken(credential)) {
            return !credential.expiresAt().isAfter(now);
        }
        Duration refreshBefore = provider.equals("instagram")
                ? properties.getInstagramRefreshBefore()
                : properties.getAccessTokenSkew();
        return !credential.expiresAt().isAfter(now.plus(refreshBefore));
    }

    private static boolean hasRefreshToken(ResolvedApiCredential credential) {
        return credential.refreshToken() != null && !credential.refreshToken().isBlank();
    }

    private void refresh(ResolvedApiCredential credential) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            RefreshedTokens tokens = switch (credential.providerName()) {
                case "twitter" -> refreshX(credential, now);
                case "linkedin" -> refreshLinkedIn(credential, now);
                case "instagram" -> refreshInstagram(credential, now);
                default -> throw new OAuthConnectionException(
                        OAuthConnectionException.CONFIGURATION_ERROR,
                        "Sosyal platform token yenilemesi desteklenmiyor"
                );
            };
            credentialService.refreshTokens(
                    credential.credentialId(),
                    new RefreshApiCredentialCommand(
                            tokens.accessToken(),
                            tokens.refreshToken(),
                            tokens.expiresAt(),
                            tokens.refreshTokenExpiresAt()
                    )
            );
            log.info("Social credential refreshed provider={} credentialId={}",
                    credential.providerName(), credential.credentialId());
        } catch (OAuthConnectionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OAuthConnectionException(
                    OAuthConnectionException.TOKEN_EXCHANGE_FAILED,
                    "Sosyal platform erişim anahtarı otomatik yenilenemedi",
                    exception
            );
        }
    }

    private RefreshedTokens refreshX(ResolvedApiCredential credential, OffsetDateTime now) {
        requireRefreshToken(credential, now);
        XOAuthClient.TokenResponse token = xOAuthClient.refreshAccessToken(credential.refreshToken());
        return new RefreshedTokens(
                token.accessToken(),
                token.refreshToken(),
                expiresAt(now, token.expiresIn(), "X"),
                null
        );
    }

    private RefreshedTokens refreshLinkedIn(ResolvedApiCredential credential, OffsetDateTime now) {
        requireRefreshToken(credential, now);
        LinkedInOAuthClient.TokenResponse token = linkedInOAuthClient.refreshAccessToken(
                credential.refreshToken()
        );
        return new RefreshedTokens(
                token.accessToken(),
                token.refreshToken(),
                expiresAt(now, token.expiresIn(), "LinkedIn"),
                token.refreshTokenExpiresIn() == null
                        ? null
                        : now.plusSeconds(token.refreshTokenExpiresIn())
        );
    }

    private RefreshedTokens refreshInstagram(ResolvedApiCredential credential, OffsetDateTime now) {
        if (credential.expiresAt() != null && !credential.expiresAt().isAfter(now)) {
            throw new OAuthConnectionException(
                    OAuthConnectionException.TOKEN_EXCHANGE_FAILED,
                    "Instagram erişim anahtarının süresi dolmuş; hesabı yeniden bağlayın"
            );
        }
        InstagramOAuthClient.LongTokenResponse token = instagramOAuthClient.refreshLongLivedToken(
                credential.accessToken()
        );
        return new RefreshedTokens(
                token.accessToken(),
                null,
                expiresAt(now, token.expiresIn(), "Instagram"),
                null
        );
    }

    private static void requireRefreshToken(ResolvedApiCredential credential, OffsetDateTime now) {
        if (credential.refreshToken() == null || credential.refreshToken().isBlank()) {
            throw new OAuthConnectionException(
                    OAuthConnectionException.CONFIGURATION_ERROR,
                    "Sosyal platform refresh token bulunamadı; hesabı yeniden bağlayın"
            );
        }
        if (credential.refreshTokenExpiresAt() != null
                && !credential.refreshTokenExpiresAt().isAfter(now)) {
            throw new OAuthConnectionException(
                    OAuthConnectionException.TOKEN_EXCHANGE_FAILED,
                    "Sosyal platform refresh token süresi dolmuş; hesabı yeniden bağlayın"
            );
        }
    }

    private static OffsetDateTime expiresAt(OffsetDateTime now, Long expiresIn, String provider) {
        if (expiresIn == null || expiresIn <= 0) {
            throw new OAuthConnectionException(
                    OAuthConnectionException.TOKEN_EXCHANGE_FAILED,
                    provider + " token yenileme yanıtında geçerlilik süresi bulunamadı"
            );
        }
        return now.plusSeconds(expiresIn);
    }

    private static String normalize(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            throw new OAuthConnectionException(
                    OAuthConnectionException.CONFIGURATION_ERROR,
                    "Sosyal platform adı bulunamadı"
            );
        }
        return providerName.trim().toLowerCase(Locale.ROOT);
    }

    private record RefreshedTokens(
            String accessToken,
            String refreshToken,
            OffsetDateTime expiresAt,
            OffsetDateTime refreshTokenExpiresAt
    ) {
    }
}
