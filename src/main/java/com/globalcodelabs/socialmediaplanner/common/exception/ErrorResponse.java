package com.globalcodelabs.socialmediaplanner.common.exception;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
                OffsetDateTime.now(ZoneOffset.UTC),
                errorCode.httpStatus().value(),
                errorCode.code(),
                message == null || message.isBlank() ? errorCode.defaultMessage() : message,
                path,
                correlationId
        );
    }
}
