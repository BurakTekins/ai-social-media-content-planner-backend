package com.globalcodelabs.socialmediaplanner.common.exception;

public class ContentOperationNotAllowedException extends DomainException {

    public ContentOperationNotAllowedException(String message) {
        super(ErrorCode.CONTENT_OPERATION_NOT_ALLOWED, message);
    }
}
