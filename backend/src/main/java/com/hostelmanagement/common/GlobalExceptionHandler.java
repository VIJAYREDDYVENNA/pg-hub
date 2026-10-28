package com.hostelmanagement.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Converts exceptions into consistent, non-leaky JSON error responses. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest req) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), null, req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Please correct the highlighted fields", errors, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid request parameters", errors, req);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleBadInput(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Malformed or missing request data", null, req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation on {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "CONFLICT",
                "This change conflicts with existing data (for example a bed that is already allocated or a duplicate record)",
                null, req);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimistic(OptimisticLockingFailureException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "The record was changed by someone else. Please reload and try again.", null, req);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiError> handleDenied(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action", null, req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required", null, req);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "The file is larger than the 5 MB limit", null, req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", null, req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed", null, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong. Please try again or contact support.", null, req);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           Map<String, String> fieldErrors, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status.value(), code, message, fieldErrors, req.getRequestURI()));
    }
}
