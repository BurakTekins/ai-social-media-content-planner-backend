package com.globalcodelabs.socialmediaplanner.common.exception;

import com.globalcodelabs.socialmediaplanner.domain.enums.ContentStatus;

public class InvalidContentStateTransitionException extends DomainException {

    public InvalidContentStateTransitionException(ContentStatus from, ContentStatus to) {
        super(
                ErrorCode.INVALID_CONTENT_STATE_TRANSITION,
                "Content status cannot transition from %s to %s".formatted(from, to)
        );
    }
}
