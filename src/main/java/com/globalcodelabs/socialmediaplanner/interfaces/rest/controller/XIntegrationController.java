package com.globalcodelabs.socialmediaplanner.interfaces.rest.controller;

import com.globalcodelabs.socialmediaplanner.application.service.XIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/x")
@RequiredArgsConstructor
@Slf4j
public class XIntegrationController {

    private final XIntegrationService xIntegrationService;

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize() {
        return OAuthControllerSupport.authorize(xIntegrationService.authorizationUri());
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        return OAuthControllerSupport.callback(
                "twitter",
                error,
                () -> xIntegrationService.completeAuthorization(code, state),
                xIntegrationService::frontendRedirectUri,
                log
        );
    }

    @PostMapping("/validate")
    public XIntegrationService.ConnectionResult validate() {
        return xIntegrationService.validateConnectedAccount();
    }
}
