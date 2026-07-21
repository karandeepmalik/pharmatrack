package com.pharma.inventory.mapper;

import com.pharma.inventory.dto.ScreenshotDto;
import com.pharma.inventory.dto.TransactionResponse;
import com.pharma.inventory.entity.Transaction;
import com.pharma.inventory.entity.TransactionScreenshot;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps Transaction entities to TransactionResponse DTOs.
 *
 * Extracted from TransactionService to uphold the Single Responsibility Principle:
 * the service handles business logic; this class handles data shape transformation.
 */
@Component
public class TransactionMapper {

    /**
     * Converts a fully-loaded Transaction entity to its API response representation.
     *
     * @param t the Transaction entity (must have submittedBy and medicine eagerly loaded
     *          or called within an active transaction)
     * @return the corresponding TransactionResponse DTO
     */
    public TransactionResponse toResponse(Transaction t) {
        TransactionResponse r = new TransactionResponse();

        r.setId(t.getId());

        // Submitter fields
        r.setSubmittedById(t.getSubmittedBy().getId());
        r.setSubmittedByUsername(t.getSubmittedBy().getUsername());
        r.setSubmittedByFullName(t.getSubmittedBy().getFullName());

        // Medicine fields
        r.setMedicineId(t.getMedicine().getId());
        r.setMedicineName(t.getMedicine().getName());
        r.setMedicineType(t.getMedicine().getType().name());
        r.setSpecification(t.getMedicine().getSpecification());
        r.setConcentrationMgPerMl(t.getMedicine().getConcentrationMgPerMl());

        // Pharma fields (navigated through medicine → pharmaCompany)
        r.setPharmaName(t.getMedicine().getPharmaCompany().getName());
        r.setPharmaId(t.getMedicine().getPharmaCompany().getId());

        r.setPrice(t.getMedicine().getPrice());
        r.setPricePerUnit(t.getPricePerUnit());

        // Transaction fields
        r.setQuantity(t.getQuantity());
        r.setStatus(t.getStatus().name());
        r.setInventoryType(t.getInventoryType() != null ? t.getInventoryType().name() : "REGULAR_MEDICINE_STOCK");
        r.setSubmittedAt(t.getSubmittedAt());
        r.setNotes(t.getNotes());

        // Screenshots — prefer new table; fall back to legacy columns for old records
        List<TransactionScreenshot> shots = t.getScreenshots();
        if (shots != null && !shots.isEmpty()) {
            r.setScreenshots(shots.stream()
                    .map(s -> new ScreenshotDto(s.getData(), s.getMimeType()))
                    .toList());
        } else if (t.getPaymentScreenshot() != null) {
            r.setScreenshots(List.of(
                    new ScreenshotDto(t.getPaymentScreenshot(), t.getPaymentScreenshotType())));
        }

        // Optional approver (only set after APPROVED or REJECTED)
        if (t.getApprovedBy() != null) {
            r.setApprovedByUsername(t.getApprovedBy().getUsername());
            r.setApprovedAt(t.getApprovedAt());
        }

        return r;
    }
}
