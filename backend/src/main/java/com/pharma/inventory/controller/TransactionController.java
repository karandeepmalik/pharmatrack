package com.pharma.inventory.controller;

import com.pharma.inventory.dto.ApprovalRequest;
import com.pharma.inventory.dto.TransactionRequest;
import com.pharma.inventory.dto.TransactionResponse;
import com.pharma.inventory.service.ScreenshotProcessor;
import com.pharma.inventory.service.TransactionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
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
            @RequestParam(value = "screenshots", required = false) List<MultipartFile> screenshots,
            @RequestParam(value = "pricePerUnit", required = false) Integer pricePerUnit,
            @RequestParam(value = "inventoryType", required = false) String inventoryType,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        TransactionRequest req = buildRequest(medicineId, quantity, notes, screenshots, pricePerUnit, inventoryType);
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

    /**
     * Admin endpoint to browse transaction history by date range and status.
     *
     * @param from   start date inclusive (YYYY-MM-DD)
     * @param to     end date inclusive (YYYY-MM-DD)
     * @param status ALL | APPROVED | REJECTED  (default ALL)
     */
    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TransactionResponse>> getHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "ALL") String status) {
        return ResponseEntity.ok(transactionService.getHistory(from, to, status));
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
                                             String notes, List<MultipartFile> screenshots,
                                             Integer pricePerUnit, String inventoryType)
            throws IOException {
        TransactionRequest req = new TransactionRequest();
        req.setMedicineId(medicineId);
        req.setQuantity(quantity);
        req.setNotes(notes);
        req.setPricePerUnit(pricePerUnit);
        req.setInventoryType(inventoryType);

        List<String[]> encoded = screenshotProcessor.encodeAll(screenshots);
        if (encoded.isEmpty()) {
            throw new com.pharma.inventory.exception.InvalidScreenshotException(
                    "At least one payment screenshot is required.");
        }
        req.setPaymentScreenshots(encoded.stream().map(e -> e[0]).toList());
        req.setPaymentScreenshotTypes(encoded.stream().map(e -> e[1]).toList());
        return req;
    }
}
