package com.consoleadda.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Structured error body returned by every failing API call. Never contains stack traces.
 *
 * @param code machine-readable error code the frontend can switch on (e.g. {@code STATION_OCCUPIED})
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldViolation> validationErrors
) {

    public record FieldViolation(String field, String message) {
    }
}
