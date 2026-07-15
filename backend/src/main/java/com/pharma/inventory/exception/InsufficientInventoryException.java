package com.pharma.inventory.exception;

import java.math.BigDecimal;

/**
 * Thrown when a transaction requests more quantity than is available in inventory.
 * Maps to HTTP 409 Conflict via GlobalExceptionHandler.
 */
public class InsufficientInventoryException extends RuntimeException {

    private final BigDecimal available;
    private final BigDecimal requested;

    public InsufficientInventoryException(BigDecimal available, BigDecimal requested) {
        super(String.format(
            "Insufficient inventory: requested %s but only %s available", requested, available));
        this.available = available;
        this.requested = requested;
    }

    public BigDecimal getAvailable() { return available; }
    public BigDecimal getRequested() { return requested; }
}
