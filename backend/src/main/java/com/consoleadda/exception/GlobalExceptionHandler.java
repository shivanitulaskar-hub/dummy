package com.consoleadda.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/**
 * Converts every exception into an {@link ApiError}. Unexpected errors are logged with their stack trace
 * server-side and returned to the client as a generic message.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final int MYSQL_CHECK_CONSTRAINT_VIOLATED = 3819;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        List<ApiError.FieldViolation> globalErrors = ex.getBindingResult().getGlobalErrors().stream()
                .map(ge -> new ApiError.FieldViolation(ge.getObjectName(), ge.getDefaultMessage()))
                .toList();
        List<ApiError.FieldViolation> all = new java.util.ArrayList<>(violations);
        all.addAll(globalErrors);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Please correct the highlighted fields", request, all);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Please correct the highlighted fields", request, violations);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
        log.debug("Malformed request on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request is malformed or has invalid values", request, List.of());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.info("Concurrent modification on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "This record was changed by someone else. Refresh and try again.", request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "The change conflicts with existing data (duplicate or in-use record).", request, List.of());
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiError> handleAccessDenied(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to perform this action", request, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required", request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", request, List.of());
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleLockFailure(PessimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Lock contention on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "BUSY_RETRY",
                "Another counter is updating the same record. Please try again.", request, List.of());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDatabase(DataAccessException ex, HttpServletRequest request) {
        if (isCheckConstraintViolation(ex)) {
            log.warn("CHECK constraint violated on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
            return build(HttpStatus.CONFLICT, "DATA_CONFLICT",
                    "The change contains values that are not allowed (for example a negative amount).", request, List.of());
        }
        log.error("Database error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_ERROR",
                "The database is temporarily unavailable. Please try again.", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong. Please try again or contact the administrator.", request, List.of());
    }

    /** MySQL error 3819 can surface through JPA as a generic exception; detect it in the cause chain. */
    private static boolean isCheckConstraintViolation(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getErrorCode() == MYSQL_CHECK_CONSTRAINT_VIOLATED) {
                return true;
            }
        }
        return false;
    }

    public static ApiError body(HttpStatus status, String code, String message, String path,
                                List<ApiError.FieldViolation> violations) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message, path, violations);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message, HttpServletRequest request,
                                           List<ApiError.FieldViolation> violations) {
        return ResponseEntity.status(status).body(body(status, code, message, request.getRequestURI(), violations));
    }
}
