package com.globalcodelabs.socialmediaplanner.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ContentOperationNotAllowedException extends DomainException {

    public ContentOperationNotAllowedException(String message) {
        super(message);
    }
}
