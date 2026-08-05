package com.globalcodelabs.socialmediaplanner.domain.event;

import java.time.OffsetDateTime;

public interface ContentEvent {

    OffsetDateTime occurredAt();
}
