package com.pharma.inventory.controller;

import com.pharma.inventory.dto.ApprovalRequest;
import com.pharma.inventory.dto.PagedResponse;
import com.pharma.inventory.dto.TransactionRequest;
import com.pharma.inventory.dto.TransactionResponse;
import com.pharma.inventory.dto.UpdateTransactionRequest;
import com.pharma.inventory.service.ScreenshotProcessor;
import com.pharma.inventory.service.TransactionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
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
            @RequestParam(value = "submittedDate", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedDate,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        TransactionRequest req = buildRequest(medicineId, quantity, notes, screenshots, pricePerUnit, inventoryType);
        req.setSubmittedDate(submittedDate);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(transactionService.submit(req, userDetails.getUsername()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedResponse<TransactionResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ALL") String status) {
        return ResponseEntity.ok(PagedResponse.of(transactionService.getAllPaged(status, page, size)));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PagedResponse<TransactionResponse>> getMy(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(PagedResponse.of(transactionService.getByUserPaged(userDetails.getUsername(), page, size)));
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
            @Valid @RequestBody ApprovalRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                transactionService.approve(id, req, userDetails.getUsername()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/my/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteMine(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        transactionService.deleteOwnPending(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TransactionResponse> updateNotes(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTransactionRequest req) {
        return ResponseEntity.ok(transactionService.updateNotes(id, req));
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
