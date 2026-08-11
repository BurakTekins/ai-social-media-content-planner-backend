package com.globalcodelabs.socialmediaplanner.common.exception;

import java.time.OffsetDateTime;

public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String errorCode,
        String message,
        String path,
        String correlationId
) {
    public static ErrorResponse of(
            ErrorCode errorCode,
            String message,
            String path,
            String correlationId
    ) {
        return new ErrorResponse(
                OffsetDateTime.now(),
                errorCode.httpStatus().value(),
                errorCode.code(),
                message == null || message.isBlank() ? errorCode.defaultMessage() : message,
                path,
                correlationId
        );
    }
}
