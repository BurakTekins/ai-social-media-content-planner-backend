package com.globalcodelabs.socialmediaplanner.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OAuthExceptionHandler {

    @ExceptionHandler(OAuthConnectionException.class)
    public ResponseEntity<OAuthErrorResponse> handle(OAuthConnectionException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new OAuthErrorResponse(exception.errorCode(), exception.publicMessage()));
    }

    public record OAuthErrorResponse(String errorCode, String message) {
    }
}
