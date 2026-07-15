package com.pharma.inventory.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data @Builder
public class InventoryAdjustmentResponse {
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
    private String adjustedByUsername;
    private boolean inTransit;
    private int    transitDays;
    private boolean internalMovement;
    private String inventoryType;
}
