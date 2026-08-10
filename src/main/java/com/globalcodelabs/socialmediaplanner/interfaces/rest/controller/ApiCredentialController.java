package com.globalcodelabs.socialmediaplanner.interfaces.rest.controller;

import com.globalcodelabs.socialmediaplanner.application.command.CreateApiCredentialCommand;
import com.globalcodelabs.socialmediaplanner.application.command.RotateApiCredentialCommand;
import com.globalcodelabs.socialmediaplanner.application.service.ApiCredentialService;
import com.globalcodelabs.socialmediaplanner.application.service.AiCredentialValidationService;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.request.CreateApiCredentialRequest;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.request.RotateApiCredentialRequest;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.request.UpdateApiCredentialAccountIdentifierRequest;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.request.UpdateApiCredentialActiveRequest;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.response.ApiCredentialResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/credentials")
@RequiredArgsConstructor
public class ApiCredentialController {

    private final ApiCredentialService apiCredentialService;
    private final AiCredentialValidationService aiCredentialValidationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiCredentialResponse create(@Valid @RequestBody CreateApiCredentialRequest request) {
        return ApiCredentialResponse.from(apiCredentialService.create(new CreateApiCredentialCommand(
                request.credentialType(),
                request.providerName(),
                request.accountIdentifier(),
                request.accessToken(),
                request.refreshToken(),
                request.expiresAt()
        )));
    }

    @PatchMapping("/{credentialId}/account-identifier")
    public ApiCredentialResponse updateAccountIdentifier(
            @PathVariable UUID credentialId,
            @Valid @RequestBody UpdateApiCredentialAccountIdentifierRequest request
    ) {
        return ApiCredentialResponse.from(
                apiCredentialService.updateAccountIdentifier(credentialId, request.accountIdentifier())
        );
    }

    @GetMapping
    public List<ApiCredentialResponse> list() {
        return apiCredentialService.list().stream()
                .map(ApiCredentialResponse::from)
                .toList();
    }

    @GetMapping("/{credentialId}")
    public ApiCredentialResponse get(@PathVariable UUID credentialId) {
        return ApiCredentialResponse.from(apiCredentialService.get(credentialId));
    }

    @PutMapping("/{credentialId}/tokens")
    public ApiCredentialResponse rotateTokens(
            @PathVariable UUID credentialId,
            @Valid @RequestBody RotateApiCredentialRequest request
    ) {
        return ApiCredentialResponse.from(apiCredentialService.rotateTokens(
                credentialId,
                new RotateApiCredentialCommand(
                        request.accessToken(),
                        request.refreshToken(),
                        request.expiresAt()
                )
        ));
    }

    @PatchMapping("/{credentialId}/active")
    public ApiCredentialResponse changeActive(
            @PathVariable UUID credentialId,
            @Valid @RequestBody UpdateApiCredentialActiveRequest request
    ) {
        return ApiCredentialResponse.from(
                apiCredentialService.changeActive(credentialId, request.active())
        );
    }

    @PostMapping("/{credentialId}/validate")
    public ApiCredentialResponse validate(@PathVariable UUID credentialId) {
        return ApiCredentialResponse.from(aiCredentialValidationService.validate(credentialId));
    }

    @DeleteMapping("/{credentialId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID credentialId) {
        apiCredentialService.delete(credentialId);
    }
}
