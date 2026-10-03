package com.pharma.medicinestock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Request body for approving or rejecting several PENDING dispatch records in one call from
 * Review Adjustments' "Approve Selected" / "Reject Selected" bulk actions — individual per-card
 * decision buttons were removed, so this is the only way to act on a dispatch now, even a single
 * one. {@code approved} applies to the whole batch (an admin selects records to approve, or
 * records to reject — never a mix), mirroring the single-item {@link ApprovalRequest}'s own
 * approved flag. Each item carries its own optional price override, applied only when
 * {@code approved} is true (ignored for a reject batch, same as the single-item endpoint).
 */
@Data
public class BulkApprovalRequest {

    @NotNull(message = "approved must be specified")
    private Boolean approved;

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
