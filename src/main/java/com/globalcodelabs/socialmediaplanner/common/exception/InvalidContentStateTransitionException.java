package com.globalcodelabs.socialmediaplanner.common.exception;

import com.globalcodelabs.socialmediaplanner.domain.model.ContentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidContentStateTransitionException extends DomainException {

    public InvalidContentStateTransitionException(ContentStatus from, ContentStatus to) {
        super("Content status cannot transition from %s to %s".formatted(from, to));
    }
}
