package com.pharma.inventory.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public class AdjustInventoryRequest {
    @NotNull private Long userId;
    @NotNull private Long medicineId;
    @NotNull @Min(1) private Integer quantity;
    @NotBlank @Size(min = 5, max = 500) private String note;
    @NotBlank @Pattern(regexp = "ADD|REDUCE", message = "adjustmentType must be ADD or REDUCE") private String adjustmentType;

    /** Which bucket to adjust. Defaults to REGULAR_MEDICINE_STOCK if not provided. */
    private String inventoryType = "REGULAR_MEDICINE_STOCK";
    private boolean internalMovement = false;

    /** Optional date for the adjustment. Defaults to today if not provided. Cannot be in the future. */
    @PastOrPresent(message = "adjustmentDate cannot be in the future")
    private LocalDate adjustmentDate;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getMedicineId() { return medicineId; }
    public void setMedicineId(Long medicineId) { this.medicineId = medicineId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getAdjustmentType() { return adjustmentType; }
    public void setAdjustmentType(String adjustmentType) { this.adjustmentType = adjustmentType; }
    public String getInventoryType() { return inventoryType != null ? inventoryType : "REGULAR_MEDICINE_STOCK"; }
    public void setInventoryType(String inventoryType) { this.inventoryType = inventoryType; }
    public boolean isInternalMovement() { return internalMovement; }
    public void setInternalMovement(boolean internalMovement) { this.internalMovement = internalMovement; }
    public LocalDate getAdjustmentDate() { return adjustmentDate; }
    public void setAdjustmentDate(LocalDate adjustmentDate) { this.adjustmentDate = adjustmentDate; }
}
