package com.globalcodelabs.socialmediaplanner.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ContentScheduled(UUID contentId, OffsetDateTime scheduledAt, OffsetDateTime occurredAt)
        implements ContentEvent {
}
