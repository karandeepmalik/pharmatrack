package com.pharma.inventory.exception;

/**
 * Thrown when a requested resource does not exist in the database.
 * Maps to HTTP 404 via GlobalExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final Object identifier;

    public ResourceNotFoundException(String resourceType, Object identifier) {
        super(resourceType + " not found: " + identifier);
        this.resourceType = resourceType;
        this.identifier = identifier;
    }

    public String getResourceType() { return resourceType; }
    public Object getIdentifier() { return identifier; }
}
