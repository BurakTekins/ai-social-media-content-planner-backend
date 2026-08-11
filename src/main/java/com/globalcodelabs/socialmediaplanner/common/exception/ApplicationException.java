package com.globalcodelabs.socialmediaplanner.common.exception;

import java.util.Objects;

public abstract class ApplicationException extends RuntimeException {

    private final ErrorCode code;

    protected ApplicationException(ErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "Error code cannot be null");
    }

    protected ApplicationException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "Error code cannot be null");
    }

    public ErrorCode code() {
        return code;
    }

    public String publicMessage() {
        return getMessage();
    }
}
