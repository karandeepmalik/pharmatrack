package com.pharma.inventory.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps legacy DB values (REGULAR, ADMIN_STOCK) to the current enum constants
 * (REGULAR_MEDICINE_STOCK, ADMIN_MEDICINE_STOCK) so that existing rows continue
 * to work even before the DataMigrationService renames them.
 */
@Converter(autoApply = false)
public class InventoryTypeConverter implements AttributeConverter<Inventory.InventoryType, String> {

    @Override
    public String convertToDatabaseColumn(Inventory.InventoryType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public Inventory.InventoryType convertToEntityAttribute(String dbValue) {
        if (dbValue == null) return Inventory.InventoryType.REGULAR_MEDICINE_STOCK;
        return switch (dbValue) {
            case "REGULAR"              -> Inventory.InventoryType.REGULAR_MEDICINE_STOCK;
            case "ADMIN_STOCK"          -> Inventory.InventoryType.ADMIN_MEDICINE_STOCK;
            case "REGULAR_MEDICINE_STOCK" -> Inventory.InventoryType.REGULAR_MEDICINE_STOCK;
            case "ADMIN_MEDICINE_STOCK"   -> Inventory.InventoryType.ADMIN_MEDICINE_STOCK;
            default -> Inventory.InventoryType.REGULAR_MEDICINE_STOCK;
        };
    }
}
