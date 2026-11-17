package com.pharma.medicinestock.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data @Builder
public class MedicineStockAdjustmentResponse {
    private Long   id;
    private Long   userId;
    private String username;
    private String userFullName;
    private Long   medicineId;
    private String medicineName;
    private String medicineType;
    private Double specification;
    private BigDecimal quantity;
    private String adjustmentType;
    private String note;
    private String adjustedAt;
    /** Plain ISO yyyy-MM-dd of adjustedAt — lets an edit form prefill a &lt;input type="date"&gt;
     *  without having to parse the human-readable adjustedAt display string back apart. */
    private String adjustedAtDate;
    private String adjustedByUsername;
    private boolean inTransit;
    private int    transitDays;
    private boolean internalMovement;
    private String medicineStockType;
}
