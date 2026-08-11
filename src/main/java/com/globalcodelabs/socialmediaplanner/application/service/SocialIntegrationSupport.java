package com.globalcodelabs.socialmediaplanner.application.service;

import com.globalcodelabs.socialmediaplanner.common.exception.OAuthConnectionException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

final class SocialIntegrationSupport {

    private SocialIntegrationSupport() {
    }

    static String frontendRedirectUri(
            String frontendRedirectUri,
            String connectionParameter,
            String status,
            String errorCode
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .queryParam(connectionParameter, status);
        if (errorCode != null) {
            builder.queryParam("errorCode", errorCode);
        }
        return builder.build().encode().toUriString();
    }

    static void requireCode(String code, String provider) {
        if (code == null || code.isBlank()) {
            throw new OAuthConnectionException(
                    OAuthConnectionException.TOKEN_EXCHANGE_FAILED,
                    provider + " hesap bağlantısı tamamlanamadı"
            );
        }
    }

    static OAuthConnectionException tokenExchangeException(
            String provider,
            RestClientException exception
    ) {
        boolean configurationError = exception instanceof RestClientResponseException response
                && response.getStatusCode().value() == 401;
        return new OAuthConnectionException(
                configurationError
                        ? OAuthConnectionException.CONFIGURATION_ERROR
                        : OAuthConnectionException.TOKEN_EXCHANGE_FAILED,
                configurationError
                        ? provider + " uygulama yapılandırması geçersiz"
                        : provider + " erişim anahtarı alınamadı",
                exception
        );
    }

    static OAuthConnectionException accountLookupException(
            String provider,
            RestClientException exception
    ) {
        boolean permissionMissing = exception instanceof RestClientResponseException response
                && (response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403);
        return new OAuthConnectionException(
                permissionMissing
                        ? OAuthConnectionException.PERMISSION_MISSING
                        : OAuthConnectionException.ACCOUNT_LOOKUP_FAILED,
                permissionMissing
                        ? provider + " hesabı için gerekli izinler bulunmuyor"
                        : provider + " hesap bilgileri alınamadı",
                exception
        );
    }
}
