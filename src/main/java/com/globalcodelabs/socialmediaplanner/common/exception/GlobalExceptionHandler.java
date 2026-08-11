package com.globalcodelabs.socialmediaplanner.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null
                ? ErrorCode.VALIDATION_FAILED.defaultMessage()
                : fieldError.getField() + ": " + fieldError.getDefaultMessage();
        logClientError(ErrorCode.VALIDATION_FAILED, request);
        return response(ErrorCode.VALIDATION_FAILED, message, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        logClientError(ErrorCode.VALIDATION_FAILED, request);
        return response(ErrorCode.VALIDATION_FAILED, exception.getMessage(), request);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            ServletRequestBindingException.class
    })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        logClientError(ErrorCode.MALFORMED_REQUEST, request);
        return response(ErrorCode.MALFORMED_REQUEST, null, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        log.warn("Request failed errorCode={} path={} message={}",
                ErrorCode.INVALID_ARGUMENT.code(), request.getRequestURI(), exception.getMessage());
        return response(ErrorCode.INVALID_ARGUMENT, null, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        logClientError(ErrorCode.RESOURCE_NOT_FOUND, request);
        return response(ErrorCode.RESOURCE_NOT_FOUND, null, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        logClientError(ErrorCode.METHOD_NOT_ALLOWED, request);
        return response(ErrorCode.METHOD_NOT_ALLOWED, null, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        logClientError(ErrorCode.UNSUPPORTED_MEDIA_TYPE, request);
        return response(ErrorCode.UNSUPPORTED_MEDIA_TYPE, null, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handlePayloadTooLarge(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        logClientError(ErrorCode.PAYLOAD_TOO_LARGE, request);
        return response(ErrorCode.PAYLOAD_TOO_LARGE, null, request);
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleKnown(
            ApplicationException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.code();
        if (errorCode.httpStatus().is5xxServerError()) {
            log.error("Request failed errorCode={} path={} message={}",
                    errorCode.code(), request.getRequestURI(), exception.publicMessage(), exception);
        } else {
            log.warn("Request failed errorCode={} path={} message={}",
                    errorCode.code(), request.getRequestURI(), exception.publicMessage());
        }
        return response(errorCode, exception.publicMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unhandled request error path={}", request.getRequestURI(), exception);
        return response(ErrorCode.INTERNAL_SERVER_ERROR, null, request);
    }

    private static ResponseEntity<ErrorResponse> response(
            ErrorCode errorCode,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(errorCode.httpStatus()).body(ErrorResponse.of(
                errorCode,
                message,
                request.getRequestURI(),
                MDC.get("correlationId")
        ));
    }

    private static void logClientError(ErrorCode errorCode, HttpServletRequest request) {
        log.warn("Request failed errorCode={} path={}", errorCode.code(), request.getRequestURI());
    }
}
