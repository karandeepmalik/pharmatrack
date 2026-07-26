package com.pharma.medicinestock.exception;

import java.math.BigDecimal;

/**
 * Thrown when a transaction requests more quantity than is available in medicine stock.
 * Maps to HTTP 409 Conflict via GlobalExceptionHandler.
 */
public class InsufficientMedicineStockException extends RuntimeException {

    private final BigDecimal available;
    private final BigDecimal requested;

    public InsufficientMedicineStockException(BigDecimal available, BigDecimal requested) {
        super(String.format(
            "Insufficient medicineStock: requested %s but only %s available", requested, available));
        this.available = available;
        this.requested = requested;
    }

    public BigDecimal getAvailable() { return available; }
    public BigDecimal getRequested() { return requested; }
}
