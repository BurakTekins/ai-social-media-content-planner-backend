package com.globalcodelabs.socialmediaplanner.common.exception;

public class DomainException extends ApplicationException {

    public DomainException(String message) {
        this(ErrorCode.DOMAIN_RULE_VIOLATION, message);
    }

    protected DomainException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
