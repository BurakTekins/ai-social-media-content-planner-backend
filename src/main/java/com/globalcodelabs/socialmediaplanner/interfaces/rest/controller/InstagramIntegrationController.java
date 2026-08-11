package com.globalcodelabs.socialmediaplanner.interfaces.rest.controller;

import com.globalcodelabs.socialmediaplanner.application.service.InstagramIntegrationService;
import com.globalcodelabs.socialmediaplanner.common.exception.OAuthConnectionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/integrations/instagram")
@RequiredArgsConstructor
@Slf4j
public class InstagramIntegrationController {
    private final InstagramIntegrationService service;

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(service.authorizationUri())).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String status = "success";
        String errorCode = null;
        try {
            if (error != null && !error.isBlank()) {
                status = "denied";
                errorCode = "access_denied";
                log.warn("OAuth authorization denied provider=instagram errorCode={}", errorCode);
            }
            else service.complete(code, state);
        } catch (OAuthConnectionException exception) {
            status = "error";
            errorCode = exception.errorCode();
            logOAuthFailure(errorCode, exception);
        } catch (IllegalStateException exception) {
            status = "error";
            errorCode = OAuthConnectionException.CONFIGURATION_ERROR;
            log.error("OAuth callback configuration failed provider=instagram errorCode={} errorType={}",
                    errorCode, exception.getClass().getSimpleName(), exception);
        } catch (RuntimeException exception) {
            status = "error";
            errorCode = "unknown_error";
            log.error("OAuth callback failed provider=instagram errorCode={} errorType={}",
                    errorCode, exception.getClass().getSimpleName(), exception);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(service.frontendRedirectUri(status, errorCode))).build();
    }

    @PostMapping("/validate")
    public InstagramIntegrationService.ConnectionResult validate() { return service.validate(); }

    private static void logOAuthFailure(String errorCode, OAuthConnectionException exception) {
        if (exception.code().httpStatus().is5xxServerError()) {
            log.error("OAuth callback failed provider=instagram errorCode={}", errorCode, exception);
        } else {
            log.warn("OAuth callback failed provider=instagram errorCode={}", errorCode);
        }
    }
}
