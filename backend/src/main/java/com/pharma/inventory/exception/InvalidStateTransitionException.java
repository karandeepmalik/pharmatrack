package com.pharma.inventory.exception;

/**
 * Thrown when an operation is attempted on a resource in an incompatible state.
 * Example: approving an already-approved transaction.
 * Maps to HTTP 409 Conflict via GlobalExceptionHandler.
 */
public class InvalidStateTransitionException extends RuntimeException {

    private final String currentState;
    private final String attemptedOperation;

    public InvalidStateTransitionException(String currentState, String attemptedOperation) {
        super(String.format(
            "Cannot perform '%s': transaction is already in state '%s'",
            attemptedOperation, currentState));
        this.currentState = currentState;
        this.attemptedOperation = attemptedOperation;
    }

    public String getCurrentState() { return currentState; }
    public String getAttemptedOperation() { return attemptedOperation; }
}
