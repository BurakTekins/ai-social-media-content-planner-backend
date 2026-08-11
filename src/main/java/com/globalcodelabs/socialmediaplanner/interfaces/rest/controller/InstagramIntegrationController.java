package com.globalcodelabs.socialmediaplanner.interfaces.rest.controller;

import com.globalcodelabs.socialmediaplanner.application.service.InstagramIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/instagram")
@RequiredArgsConstructor
@Slf4j
public class InstagramIntegrationController {
    private final InstagramIntegrationService service;

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize() {
        return OAuthControllerSupport.authorize(service.authorizationUri());
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        return OAuthControllerSupport.callback(
                "instagram",
                error,
                () -> service.complete(code, state),
                service::frontendRedirectUri,
                log
        );
    }

    @PostMapping("/validate")
    public InstagramIntegrationService.ConnectionResult validate() {
        return service.validate();
    }
}
