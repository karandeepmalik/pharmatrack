package com.pharma.medicinestock.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Admin edit of a past medicine stock adjustment record (the "Modify Medicine Stock" history
 * previously only supported deleting a record outright — this lets an admin correct one in
 * place instead). user/medicine are not editable, mirroring TransactionRequest's own edit DTO,
 * which likewise never allows re-pointing a record at a different submitter/medicine.
 */
public class UpdateMedicineStockAdjustmentRequest {

    @NotBlank(message = "A note is required")
    @Size(min = 5, max = 500, message = "Note must be between 5 and 500 characters")
    private String note;

    /** null leaves the quantity unchanged. */
    @DecimalMin(value = "0.1", message = "Quantity must be at least 0.1")
    private BigDecimal quantity;

    /** null leaves the adjustmentType unchanged. */
    @Pattern(regexp = "ADD|REDUCE", message = "adjustmentType must be ADD or REDUCE")
    private String adjustmentType;

    /** null leaves the medicineStockType unchanged. */
    private String medicineStockType;

    /** null leaves the adjustment date unchanged. */
    @PastOrPresent(message = "adjustmentDate cannot be in the future")
    private LocalDate adjustmentDate;

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getAdjustmentType() { return adjustmentType; }
    public void setAdjustmentType(String adjustmentType) { this.adjustmentType = adjustmentType; }
    public String getMedicineStockType() { return medicineStockType; }
    public void setMedicineStockType(String medicineStockType) { this.medicineStockType = medicineStockType; }
    public LocalDate getAdjustmentDate() { return adjustmentDate; }
    public void setAdjustmentDate(LocalDate adjustmentDate) { this.adjustmentDate = adjustmentDate; }
}
