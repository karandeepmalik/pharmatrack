package com.pharma.medicinestock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Request body for approving several PENDING dispatch records in one call from
 * Review Adjustments' "Approve Selected" action. Each item carries its own optional
 * price override, mirroring the per-row price field the single-approve flow already has —
 * bulk approval does not introduce a new pricing concept, just batches the same one.
 */
@Data
public class BulkApproveRequest {

    @NotEmpty(message = "At least one transaction id is required")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull
        private Long id;
        private Integer newPrice;
    }
}
