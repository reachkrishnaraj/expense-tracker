package com.expensetracker.dto.response;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        String error,
        String code,
        Object details,
        List<FieldError> fieldErrors,
        String timestamp,
        String path
) {

    public record FieldError(String field, String message) {}

    public static ErrorResponse of(String error, String code, String path) {
        return new ErrorResponse(error, code, null, null, Instant.now().toString(), path);
    }

    public static ErrorResponse withFieldErrors(String error, String code,
                                                 List<FieldError> fieldErrors, String path) {
        return new ErrorResponse(error, code, null, fieldErrors, Instant.now().toString(), path);
    }

    public static ErrorResponse withDetails(String error, String code, Object details, String path) {
        return new ErrorResponse(error, code, details, null, Instant.now().toString(), path);
    }
}
