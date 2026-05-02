package com.pharma.inventory.exception;

/**
 * Thrown when a transaction requests more quantity than is available in inventory.
 * Maps to HTTP 409 Conflict via GlobalExceptionHandler.
 */
public class InsufficientInventoryException extends RuntimeException {

    private final int available;
    private final int requested;

    public InsufficientInventoryException(int available, int requested) {
        super(String.format(
            "Insufficient inventory: requested %d but only %d available", requested, available));
        this.available = available;
        this.requested = requested;
    }

    public int getAvailable() { return available; }
    public int getRequested() { return requested; }
}
