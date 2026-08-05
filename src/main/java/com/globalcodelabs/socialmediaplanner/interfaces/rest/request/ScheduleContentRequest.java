package com.globalcodelabs.socialmediaplanner.interfaces.rest.request;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record ScheduleContentRequest(
        @NotNull OffsetDateTime scheduledAt
) {
}
