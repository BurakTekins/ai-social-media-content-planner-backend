package com.globalcodelabs.socialmediaplanner.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ContentPublicationFailed(UUID contentId, String reason, OffsetDateTime occurredAt)
        implements ContentEvent {
}
