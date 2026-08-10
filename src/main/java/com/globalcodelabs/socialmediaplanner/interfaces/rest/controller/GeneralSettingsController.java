package com.globalcodelabs.socialmediaplanner.interfaces.rest.controller;

import com.globalcodelabs.socialmediaplanner.application.service.GeneralSettings;
import com.globalcodelabs.socialmediaplanner.application.service.GeneralSettingsService;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.request.UpdateGeneralSettingsRequest;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.response.GeneralSettingsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/settings/general")
@RequiredArgsConstructor
public class GeneralSettingsController {

    private final GeneralSettingsService generalSettingsService;

    @GetMapping
    public GeneralSettingsResponse get() {
        return GeneralSettingsResponse.from(generalSettingsService.get());
    }

    @PutMapping
    public GeneralSettingsResponse update(@Valid @RequestBody UpdateGeneralSettingsRequest request) {
        GeneralSettings settings = new GeneralSettings(
                Duration.ofSeconds(request.publicationConfirmationTimeoutSeconds()),
                Duration.ofSeconds(request.publicationConfirmationIntervalSeconds()),
                request.publishingMaxItemsPerRun()
        );
        return GeneralSettingsResponse.from(generalSettingsService.update(settings));
    }
}
