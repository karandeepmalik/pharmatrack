package com.pharma.medicinestock.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Centralised exception → HTTP status mapping for all REST controllers.
 *
 * Mapping table:
 *   ResourceNotFoundException         → 404 Not Found
 *   InsufficientMedicineStockException    → 409 Conflict
 *   InvalidStateTransitionException   → 409 Conflict
 *   InvalidScreenshotException        → 400 Bad Request
 *   IllegalArgumentException          → 400 Bad Request
 *   MethodArgumentNotValidException   → 400 Bad Request  (Bean Validation)
 *   MissingServletRequestParameter    → 400 Bad Request
 *   HttpMessageNotReadableException   → 400 Bad Request  (malformed JSON / invalid enum value)
 *   MaxUploadSizeExceededException    → 413 Payload Too Large
 *   Exception (catch-all)             → 500 Internal Server Error
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 404 Not Found ─────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNoHandler(
            Exception ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "No endpoint " + req.getMethod() + " " + req.getRequestURI(), req.getRequestURI());
    }

    // ── 409 Conflict ──────────────────────────────────────────────────

    @ExceptionHandler(InsufficientMedicineStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientMedicineStock(
            InsufficientMedicineStockException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStateTransition(
            InvalidStateTransitionException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req.getRequestURI());
    }

    // ── 400 Bad Request ───────────────────────────────────────────────

    @ExceptionHandler(InvalidScreenshotException.class)
    public ResponseEntity<ErrorResponse> handleInvalidScreenshot(
            InvalidScreenshotException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST,
            "Required parameter '" + ex.getParameterName() + "' is missing",
            req.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Malformed or invalid request body", req.getRequestURI());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(
            MissingServletRequestPartException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST,
            "Required part '" + ex.getRequestPartName() + "' is missing",
            req.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {

        BindingResult br = ex.getBindingResult();
        List<ErrorResponse.FieldError> fieldErrors = br.getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        ErrorResponse body = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Validation failed: " + fieldErrors.size() + " error(s)",
            req.getRequestURI());
        body.setFieldErrors(fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    // ── 413 Payload Too Large ─────────────────────────────────────────

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSize(
            MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE,
            "Payment screenshot must be smaller than 5 MB",
            req.getRequestURI());
    }

    // ── 500 Internal Server Error (safety net) ────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest req) {
        // A handler that returns cleanly (as this one does) is never logged by the servlet
        // container's own unhandled-exception path — unlike an exception thrown from a filter
        // (which runs before this advice even applies), the exception here is technically
        // "handled" the moment this method returns, so nothing else will ever log it. Without
        // this line, every unexpected 500 would be completely invisible in Cloud Logging: the
        // client gets a clean generic message, but there'd be zero server-side trace of what
        // actually failed or why.
        log.error("Unhandled exception on {} {}: {}", req.getMethod(), req.getRequestURI(), ex.toString(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again later.",
            req.getRequestURI());
    }

    // ── Helper ────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, String path) {
        ErrorResponse body = new ErrorResponse(
            status.value(), status.getReasonPhrase(), message, path);
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleAuthFailed(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password", req.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "Access denied", req.getRequestURI());
    }
}
