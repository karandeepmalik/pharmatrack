package com.pharma.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotBlank(message = "An adjustment note is required — explain why this dispatch is being made")
    @Size(min = 5, max = 500, message = "Note must be between 5 and 500 characters")
    private String notes;

    /**
     * Base64-encoded content of the payment screenshot.
     * Populated by the controller after reading the uploaded MultipartFile.
     * Optional — may be null if no screenshot was attached.
     */
    private String paymentScreenshot;

    /**
     * MIME type of the payment screenshot, e.g. "image/png", "image/jpeg".
     * Populated alongside paymentScreenshot.
     */
    private String paymentScreenshotType;

    /**
     * Optional price per unit override submitted by the user.
     * If null, the medicine's current price is used at approval time.
     */
    private Integer pricePerUnit;

    // ── Getters & Setters ──────────────────────────────────────────────

    public Long getMedicineId() { return medicineId; }
    public void setMedicineId(Long medicineId) { this.medicineId = medicineId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getPaymentScreenshot() { return paymentScreenshot; }
    public void setPaymentScreenshot(String paymentScreenshot) {
        this.paymentScreenshot = paymentScreenshot;
    }

    public String getPaymentScreenshotType() { return paymentScreenshotType; }
    public void setPaymentScreenshotType(String paymentScreenshotType) {
        this.paymentScreenshotType = paymentScreenshotType;
    }

    public Integer getPricePerUnit() { return pricePerUnit; }
    public void setPricePerUnit(Integer pricePerUnit) { this.pricePerUnit = pricePerUnit; }
}
