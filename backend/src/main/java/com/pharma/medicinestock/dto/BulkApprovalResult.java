package com.pharma.medicinestock.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Per-item outcome of a bulk approve/reject. A partial failure (e.g. one record was already
 * acted on by another admin between page load and the bulk action) must not abort the rest
 * of the batch, so every item gets its own success/error result rather than a single
 * all-or-nothing response. {@code success} means the requested action (approve or reject)
 * went through for this item — it does not itself say which action that was.
 */
@Data
@Builder
public class BulkApprovalResult {
    private Long id;
    private boolean success;
    private String error;

    public static BulkApprovalResult success(Long id) {
        return BulkApprovalResult.builder().id(id).success(true).build();
    }

    public static BulkApprovalResult failure(Long id, String error) {
        return BulkApprovalResult.builder().id(id).success(false).error(error).build();
    }
}
