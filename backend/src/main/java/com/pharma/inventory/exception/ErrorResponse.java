package com.pharma.inventory.exception;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Uniform JSON error envelope returned for all API error responses.
 *
 * <pre>
 * {
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "User not found: john.doe",
 *   "path": "/api/transactions",
 *   "timestamp": "2026-04-23T10:00:00"
 * }
 * </pre>
 */
public class ErrorResponse {

    private final int status;
    private final String error;
    private final String message;
    private final String path;
    private final LocalDateTime timestamp;

    /** Extra field list for validation errors (field → message pairs). */
    private List<FieldError> fieldErrors;

    public ErrorResponse(int status, String error, String message, String path) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.timestamp = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
    }

    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public List<FieldError> getFieldErrors() { return fieldErrors; }
    public void setFieldErrors(List<FieldError> fieldErrors) { this.fieldErrors = fieldErrors; }

    public record FieldError(String field, String message) {}
}
