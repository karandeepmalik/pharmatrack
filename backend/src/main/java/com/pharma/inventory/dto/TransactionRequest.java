package com.pharma.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for submitting an inventory adjustment (dispatch) request.
 *
 * The payment screenshot is handled separately via MultipartFile in the controller
 * (to allow multipart/form-data upload), but its Base64-encoded value and MIME type
 * are stored here after the controller processes the upload.
 */
public class TransactionRequest {

    @NotNull(message = "Medicine ID is required")
    private Long medicineId;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.1", message = "Quantity must be at least 0.1")
    private BigDecimal quantity;

    @NotBlank(message = "An adjustment note is required — explain why this dispatch is being made")
    @Size(min = 5, max = 500, message = "Note must be between 5 and 500 characters")
    private String notes;

    /** Base64-encoded content of each payment screenshot. Populated by the controller. */
    private List<String> paymentScreenshots = new ArrayList<>();

    /** MIME type for each screenshot (parallel list with paymentScreenshots). */
    private List<String> paymentScreenshotTypes = new ArrayList<>();

    /**
     * Optional price per unit override submitted by the user.
     * If null, the medicine's current price is used at approval time.
     */
    private Integer pricePerUnit;

    /**
     * The inventory bucket to deduct from: REGULAR_MEDICINE_STOCK or ADMIN_MEDICINE_STOCK.
     * Defaults to REGULAR_MEDICINE_STOCK if not provided.
     */
    private String inventoryType;

    /**
     * Optional date the dispatch occurred. If null, defaults to the current date/time.
     * Must not be in the future.
     */
    private LocalDate submittedDate;

    // ── Getters & Setters ──────────────────────────────────────────────

    public Long getMedicineId() { return medicineId; }
    public void setMedicineId(Long medicineId) { this.medicineId = medicineId; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<String> getPaymentScreenshots() { return paymentScreenshots; }
    public void setPaymentScreenshots(List<String> paymentScreenshots) {
        this.paymentScreenshots = paymentScreenshots;
    }

    public List<String> getPaymentScreenshotTypes() { return paymentScreenshotTypes; }
    public void setPaymentScreenshotTypes(List<String> paymentScreenshotTypes) {
        this.paymentScreenshotTypes = paymentScreenshotTypes;
    }

    public Integer getPricePerUnit() { return pricePerUnit; }
    public void setPricePerUnit(Integer pricePerUnit) { this.pricePerUnit = pricePerUnit; }

    public String getInventoryType() { return inventoryType; }
    public void setInventoryType(String inventoryType) { this.inventoryType = inventoryType; }

    public LocalDate getSubmittedDate() { return submittedDate; }
    public void setSubmittedDate(LocalDate submittedDate) { this.submittedDate = submittedDate; }
}
