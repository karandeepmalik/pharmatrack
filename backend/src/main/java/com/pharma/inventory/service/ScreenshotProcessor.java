package com.pharma.inventory.service;

import com.pharma.inventory.exception.InvalidScreenshotException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.Set;

/**
 * Handles validation and Base64 encoding of uploaded payment screenshots.
 *
 * Extracted from TransactionController to uphold SRP:
 * the controller handles HTTP routing; this service handles file processing.
 */
@Service
public class ScreenshotProcessor {

    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MB

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "image/png",
        "image/jpeg",
        "image/jpg",
        "image/webp",
        "image/gif"
    );

    /**
     * Validates a screenshot MultipartFile and returns its Base64-encoded content.
     *
     * @param file the uploaded file (must not be null or empty)
     * @return Base64-encoded string of the file bytes
     * @throws InvalidScreenshotException if the MIME type is invalid or file is too large
     * @throws IOException                if the file bytes cannot be read
     */
    public String encodeToBase64(MultipartFile file) throws IOException {
        validateContentType(file);
        validateSize(file);
        return Base64.getEncoder().encodeToString(file.getBytes());
    }

    /**
     * Returns whether a given MultipartFile is a non-null, non-empty image.
     */
    public boolean hasScreenshot(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    // ── Private validation ────────────────────────────────────────────

    private void validateContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidScreenshotException(
                "Payment screenshot must be an image file (PNG, JPEG, WebP, GIF). " +
                "Received: " + contentType);
        }
    }

    private void validateSize(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidScreenshotException(
                "Payment screenshot must be smaller than 5 MB. " +
                "Received: " + (file.getSize() / 1024 / 1024) + " MB");
        }
    }
}
