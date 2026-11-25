package com.pharma.medicinestock.controller;

import com.pharma.medicinestock.dto.ApprovalRequest;
import com.pharma.medicinestock.dto.BulkApprovalRequest;
import com.pharma.medicinestock.dto.BulkApprovalResult;
import com.pharma.medicinestock.dto.PagedResponse;
import com.pharma.medicinestock.dto.TransactionRequest;
import com.pharma.medicinestock.dto.TransactionResponse;
import com.pharma.medicinestock.service.ScreenshotProcessor;
import com.pharma.medicinestock.service.TransactionService;
import org.springframework.data.domain.Page;
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
import java.math.BigDecimal;
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
            @RequestParam("quantity") BigDecimal quantity,
            @RequestParam("notes") String notes,
            @RequestParam(value = "screenshots", required = false) List<MultipartFile> screenshots,
            @RequestParam(value = "pricePerUnit", required = false) Integer pricePerUnit,
            @RequestParam(value = "medicineStockType", required = false) String medicineStockType,
            @RequestParam(value = "submittedDate", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedDate,
            @RequestParam(value = "idempotencyKey", required = false) String idempotencyKey,
            @RequestParam(value = "paymentStatus", required = false) String paymentStatus,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        TransactionRequest req = buildRequest(medicineId, quantity, notes, screenshots, pricePerUnit, medicineStockType);
        req.setSubmittedDate(submittedDate);
        req.setIdempotencyKey(idempotencyKey);
        req.setPaymentStatus(paymentStatus);
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
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) List<Long> medicineId,
            @RequestParam(required = false) String notes,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(PagedResponse.of(
                transactionService.getByUserPaged(userDetails.getUsername(), page, size, status, medicineId, notes)));
    }

    /**
     * Admin endpoint to browse transaction history by date range, with optional filters.
     * All filters are applied server-side so they're correct regardless of pagination —
     * a filter that only matched against whatever page happened to be loaded client-side would
     * silently miss real results. Every filter accepts zero or more values (repeated query
     * params, e.g. {@code ?username=alice&username=bob}) — an empty/omitted list means "no
     * filter on this field", matching the previous single-value "ALL" convention.
     *
     * @param from       start date inclusive (YYYY-MM-DD)
     * @param to         end date inclusive (YYYY-MM-DD)
     * @param status     zero or more of APPROVED | REJECTED  (empty = all)
     * @param username   zero or more exact submittedBy username filters
     * @param medicineId zero or more exact medicine filters
     * @param notes      optional case-insensitive notes substring filter
     * @param medicineStockType zero or more of REGULAR_MEDICINE_STOCK/ADMIN_MEDICINE_STOCK
     * @param paymentStatus zero or more of PAID/UNPAID/PARTIAL_PAYMENT
     */
    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedResponse<TransactionResponse>> getHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) List<String> username,
            @RequestParam(required = false) List<Long> medicineId,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) List<String> medicineStockType,
            @RequestParam(required = false) List<String> paymentStatus) {
        Page<TransactionResponse> result = transactionService.getHistory(from, to, status, page, size, username, medicineId, notes, medicineStockType, paymentStatus);
        BigDecimal totalQuantity = transactionService.getHistoryTotalQuantity(from, to, status, username, medicineId, notes, medicineStockType, paymentStatus);
        return ResponseEntity.ok(PagedResponse.of(result, totalQuantity));
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

    /**
     * Approves or rejects several PENDING records in one call — Review Adjustments'
     * "Approve Selected" / "Reject Selected" bulk actions, and (since the per-card decision
     * buttons were removed) the only way to act on a dispatch now, even a single one.
     * {@code approved} applies to the whole batch. Each item's outcome is reported
     * independently (see {@link TransactionService#approveBulk}); this is intentionally not
     * all-or-nothing, so the response is always 200 with a per-item result list, even if some
     * items failed.
     */
    @PostMapping("/approve-bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BulkApprovalResult>> approveBulk(
            @Valid @RequestBody BulkApprovalRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                transactionService.approveBulk(req, userDetails.getUsername()));
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

    /**
     * Admin edit of a past dispatch record — every field is correctable after the fact
     * (including on an already-APPROVED record), not just notes. {@code notes} is always
     * required, deliberately: it's this endpoint's only audit trail for a change to quantity
     * or stock type, since — unlike {@code /medicine-stock/adjust} — there's no separate
     * adjustment-ledger entry recorded for it.
     *
     * @param quantity          optional — omit to leave unchanged
     * @param medicineStockType optional — omit to leave unchanged
     * @param pricePerUnit      optional — omit to leave unchanged; overrides the price recorded
     *                          for this dispatch (independent of the medicine's catalogue price)
     * @param submittedDate     optional — omit to leave unchanged; corrects the dispatch date
     * @param screenshots       optional — if provided (non-empty), replaces the existing set
     *                          entirely; omit to leave existing screenshots unchanged
     * @param paymentStatus     optional — omit to leave unchanged; PAID/UNPAID/PARTIAL_PAYMENT.
     *                          Unlike quantity/stock-type, this is never gated by transaction
     *                          status — payment can be settled well after a dispatch is approved.
     * @param userId            optional — omit to leave unchanged; re-points the dispatch at a
     *                           different (non-admin) user, correcting whoever actually submitted it
     * @param medicineId        optional — omit to leave unchanged; corrects the medicine brand/spec
     *                           the dispatch was actually for
     */
    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @PathVariable Long id,
            @RequestParam("notes") String notes,
            @RequestParam(value = "quantity", required = false) BigDecimal quantity,
            @RequestParam(value = "medicineStockType", required = false) String medicineStockType,
            @RequestParam(value = "pricePerUnit", required = false) Integer pricePerUnit,
            @RequestParam(value = "submittedDate", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedDate,
            @RequestParam(value = "screenshots", required = false) List<MultipartFile> screenshots,
            @RequestParam(value = "paymentStatus", required = false) String paymentStatus,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "medicineId", required = false) Long medicineId)
            throws IOException {
        // Encoding here, not in the service, mirrors submit()/buildRequest() — screenshot
        // compression is an HTTP-boundary concern (MultipartFile), not business logic.
        // encodeAll(...) already treats null/empty input as "nothing to encode", which is
        // exactly this endpoint's "screenshots omitted -> leave existing ones alone" contract.
        List<String[]> encodedScreenshots = screenshotProcessor.encodeAll(screenshots);
        return ResponseEntity.ok(
                transactionService.updateTransaction(id, notes, quantity, medicineStockType, pricePerUnit, submittedDate, encodedScreenshots, paymentStatus, userId, medicineId));
    }

    // ── Assembly helper (no logic — only construction) ─────────────────

    private TransactionRequest buildRequest(Long medicineId, BigDecimal quantity,
                                             String notes, List<MultipartFile> screenshots,
                                             Integer pricePerUnit, String medicineStockType)
            throws IOException {
        TransactionRequest req = new TransactionRequest();
        req.setMedicineId(medicineId);
        req.setQuantity(quantity);
        req.setNotes(notes);
        req.setPricePerUnit(pricePerUnit);
        req.setMedicineStockType(medicineStockType);

        List<String[]> encoded = screenshotProcessor.encodeAll(screenshots);
        if (encoded.isEmpty()) {
            throw new com.pharma.medicinestock.exception.InvalidScreenshotException(
                    "At least one payment screenshot is required.");
        }
        req.setPaymentScreenshots(encoded.stream().map(e -> e[0]).toList());
        req.setPaymentScreenshotTypes(encoded.stream().map(e -> e[1]).toList());
        return req;
    }
}
