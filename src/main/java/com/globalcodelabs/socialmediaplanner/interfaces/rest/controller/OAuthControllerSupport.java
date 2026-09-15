package com.globalcodelabs.socialmediaplanner.interfaces.rest.controller;

import com.globalcodelabs.socialmediaplanner.common.exception.OAuthConnectionException;
import com.globalcodelabs.socialmediaplanner.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.function.BiFunction;

final class OAuthControllerSupport {

    private OAuthControllerSupport() {
    }

    static ResponseEntity<Void> authorize(String authorizationUri) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authorizationUri))
                .build();
    }

    static ResponseEntity<Void> callback(
            String provider,
            String authorizationError,
            Runnable completeAuthorization,
            BiFunction<String, String, String> redirectUri,
            Logger log
    ) {
        String status = "success";
        String errorCode = null;
        try {
            if (authorizationError != null && !authorizationError.isBlank()) {
                status = "denied";
                errorCode = "access_denied";
                log.warn("OAuth authorization denied provider={} errorCode={}", provider, errorCode);
            } else {
                completeAuthorization.run();
            }
        } catch (OAuthConnectionException exception) {
            status = "error";
            errorCode = exception.errorCode();
            logOAuthFailure(provider, errorCode, exception, log);
        } catch (IllegalStateException exception) {
            status = "error";
            errorCode = ErrorCode.OAUTH_CONFIGURATION_ERROR.code();
            log.error("OAuth callback configuration failed provider={} errorCode={} errorType={}",
                    provider, errorCode, exception.getClass().getSimpleName(), exception);
        } catch (RuntimeException exception) {
            status = "error";
            errorCode = "unknown_error";
            log.error("OAuth callback failed provider={} errorCode={} errorType={}",
                    provider, errorCode, exception.getClass().getSimpleName(), exception);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUri.apply(status, errorCode)))
                .build();
    }

    private static void logOAuthFailure(
            String provider,
            String errorCode,
            OAuthConnectionException exception,
            Logger log
    ) {
        if (exception.code().httpStatus().is5xxServerError()) {
            log.error("OAuth callback failed provider={} errorCode={}", provider, errorCode, exception);
        } else {
            log.warn("OAuth callback failed provider={} errorCode={}", provider, errorCode);
        }
    }
}
