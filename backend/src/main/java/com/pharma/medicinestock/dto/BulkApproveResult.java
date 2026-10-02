package com.pharma.medicinestock.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Per-item outcome of a bulk approval. A partial failure (e.g. one record was already
 * acted on by another admin between page load and the bulk action) must not abort the
 * rest of the batch, so every item gets its own success/error result rather than a single
 * all-or-nothing response.
 */
@Data
@Builder
public class BulkApproveResult {
    private Long id;
    private boolean approved;
    private String error;

    public static BulkApproveResult success(Long id) {
        return BulkApproveResult.builder().id(id).approved(true).build();
    }

    public static BulkApproveResult failure(Long id, String error) {
        return BulkApproveResult.builder().id(id).approved(false).error(error).build();
    }
}
