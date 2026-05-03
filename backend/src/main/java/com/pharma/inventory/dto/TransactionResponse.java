package com.pharma.inventory.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for a transaction / adjustment record.
 * Includes the optional payment screenshot (Base64) so the
 * Admin review page can render it inline without a second round-trip.
 */
public class TransactionResponse {

    private Long id;
    private Long submittedById;
    private String submittedByUsername;
    private String submittedByFullName;
    private Long medicineId;
    private String medicineName;
    private String medicineType;
    private Double specification;
    private Double concentrationMgPerMl;
    private String pharmaName;
    private Long pharmaId;
    private Integer quantity;
    private String status;
    private LocalDateTime submittedAt;
    private String approvedByUsername;
    private LocalDateTime approvedAt;
    private String notes;

    /**
     * Base64-encoded payment screenshot. May be null when no screenshot was uploaded.
     * Frontend should check for null before rendering.
     */
    private String paymentScreenshot;

    /** MIME type for the screenshot data URI, e.g. "image/png". */
    private String paymentScreenshotType;

    // ── Getters & Setters ─────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSubmittedById() { return submittedById; }
    public void setSubmittedById(Long submittedById) { this.submittedById = submittedById; }

    public String getSubmittedByUsername() { return submittedByUsername; }
    public void setSubmittedByUsername(String submittedByUsername) {
        this.submittedByUsername = submittedByUsername;
    }

    public String getSubmittedByFullName() { return submittedByFullName; }
    public void setSubmittedByFullName(String submittedByFullName) {
        this.submittedByFullName = submittedByFullName;
    }

    public Long getMedicineId() { return medicineId; }
    public void setMedicineId(Long medicineId) { this.medicineId = medicineId; }

    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String medicineName) { this.medicineName = medicineName; }

    public String getMedicineType() { return medicineType; }
    public void setMedicineType(String medicineType) { this.medicineType = medicineType; }

    public Double getSpecification() { return specification; }
    public void setSpecification(Double specification) { this.specification = specification; }

    public Double getConcentrationMgPerMl() { return concentrationMgPerMl; }
    public void setConcentrationMgPerMl(Double concentrationMgPerMl) { this.concentrationMgPerMl = concentrationMgPerMl; }

    public String getPharmaName() { return pharmaName; }
    public void setPharmaName(String pharmaName) { this.pharmaName = pharmaName; }

    public Long getPharmaId() { return pharmaId; }
    public void setPharmaId(Long pharmaId) { this.pharmaId = pharmaId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public String getApprovedByUsername() { return approvedByUsername; }
    public void setApprovedByUsername(String approvedByUsername) {
        this.approvedByUsername = approvedByUsername;
    }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getPaymentScreenshot() { return paymentScreenshot; }
    public void setPaymentScreenshot(String paymentScreenshot) {
        this.paymentScreenshot = paymentScreenshot;
    }

    public String getPaymentScreenshotType() { return paymentScreenshotType; }
    public void setPaymentScreenshotType(String paymentScreenshotType) {
        this.paymentScreenshotType = paymentScreenshotType;
    }
}
