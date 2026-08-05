package com.globalcodelabs.socialmediaplanner.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ContentPublished(UUID contentId, OffsetDateTime occurredAt) implements ContentEvent {
}
