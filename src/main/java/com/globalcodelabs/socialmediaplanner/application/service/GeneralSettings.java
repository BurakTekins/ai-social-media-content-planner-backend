package com.globalcodelabs.socialmediaplanner.application.service;

import java.time.Duration;

public record GeneralSettings(
        Duration publicationConfirmationTimeout,
        Duration publicationConfirmationInterval,
        int publishingMaxItemsPerRun
) {
}
