package com.pharma.inventory.exception;

/**
 * Thrown when an uploaded payment screenshot fails validation
 * (wrong MIME type or exceeds size limit).
 * Maps to HTTP 400 Bad Request via GlobalExceptionHandler.
 */
public class InvalidScreenshotException extends RuntimeException {

    public InvalidScreenshotException(String message) {
        super(message);
    }
}
