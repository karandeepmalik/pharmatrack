package com.pharma.inventory.controller;

import com.pharma.inventory.dto.ApprovalRequest;
import com.pharma.inventory.dto.TransactionRequest;
import com.pharma.inventory.dto.TransactionResponse;
import com.pharma.inventory.service.ScreenshotProcessor;
import com.pharma.inventory.service.TransactionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Thin HTTP adapter for transaction operations.
 *
 * Single responsibility: parse HTTP request → delegate to service → return response.
 * No business logic. No validation logic. No mapping logic.
 * All exceptions handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final ScreenshotProcessor screenshotProcessor;

    public TransactionController(TransactionService transactionService,
                                 ScreenshotProcessor screenshotProcessor) {
        this.transactionService = transactionService;
        this.screenshotProcessor = screenshotProcessor;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TransactionResponse> submit(
            @RequestParam("medicineId") Long medicineId,
            @RequestParam("quantity") Integer quantity,
            @RequestParam("notes") String notes,
            @RequestParam("screenshot") MultipartFile screenshot,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        TransactionRequest req = buildRequest(medicineId, quantity, notes, screenshot);
        return ResponseEntity.ok(transactionService.submit(req, userDetails.getUsername()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TransactionResponse>> getAll() {
        return ResponseEntity.ok(transactionService.getAll());
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<TransactionResponse>> getMy(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(transactionService.getByUser(userDetails.getUsername()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TransactionResponse> approve(
            @PathVariable Long id,
            @RequestBody ApprovalRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                transactionService.approve(id, req, userDetails.getUsername()));
    }

    // ── Assembly helper (no logic — only construction) ─────────────────

    private TransactionRequest buildRequest(Long medicineId, Integer quantity,
                                             String notes, MultipartFile screenshot)
            throws IOException {
        TransactionRequest req = new TransactionRequest();
        req.setMedicineId(medicineId);
        req.setQuantity(quantity);
        req.setNotes(notes);

        if (screenshotProcessor.hasScreenshot(screenshot)) {
            req.setPaymentScreenshot(screenshotProcessor.encodeToBase64(screenshot));
            req.setPaymentScreenshotType(screenshot.getContentType());
        }
        return req;
    }
}
