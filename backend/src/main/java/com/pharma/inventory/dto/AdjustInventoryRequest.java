package com.pharma.inventory.dto;

import jakarta.validation.constraints.*;

public class AdjustInventoryRequest {
    @NotNull private Long userId;
    @NotNull private Long medicineId;
    @NotNull @Min(1) private Integer quantity;
    @NotBlank @Size(min = 5, max = 500) private String note;
    @NotBlank @Pattern(regexp = "ADD|REDUCE", message = "adjustmentType must be ADD or REDUCE") private String adjustmentType;

    /** Which bucket to adjust. Defaults to REGULAR if not provided. */
    private String inventoryType = "REGULAR";

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
    public String getInventoryType() { return inventoryType != null ? inventoryType : "REGULAR"; }
    public void setInventoryType(String inventoryType) { this.inventoryType = inventoryType; }
}
