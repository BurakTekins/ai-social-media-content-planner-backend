package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.application.service.GeneralSettings;

public record GeneralSettingsResponse(
        long publicationConfirmationTimeoutSeconds,
        long publicationConfirmationIntervalSeconds,
        int publishingMaxItemsPerRun
) {
    public static GeneralSettingsResponse from(GeneralSettings settings) {
        return new GeneralSettingsResponse(
                settings.publicationConfirmationTimeout().toSeconds(),
                settings.publicationConfirmationInterval().toSeconds(),
                settings.publishingMaxItemsPerRun()
        );
    }
}
