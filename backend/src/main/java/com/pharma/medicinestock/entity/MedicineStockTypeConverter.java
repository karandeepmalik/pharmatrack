package com.pharma.medicinestock.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps legacy DB values (REGULAR, ADMIN_STOCK) to the current enum constants
 * (REGULAR_MEDICINE_STOCK, ADMIN_MEDICINE_STOCK) so that existing rows continue
 * to work even before the DataMigrationService renames them.
 */
@Converter(autoApply = false)
public class MedicineStockTypeConverter implements AttributeConverter<MedicineStock.MedicineStockType, String> {

    @Override
    public String convertToDatabaseColumn(MedicineStock.MedicineStockType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public MedicineStock.MedicineStockType convertToEntityAttribute(String dbValue) {
        if (dbValue == null) return MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK;
        return switch (dbValue) {
            case "REGULAR"              -> MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK;
            case "ADMIN_STOCK"          -> MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK;
            case "REGULAR_MEDICINE_STOCK" -> MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK;
            case "ADMIN_MEDICINE_STOCK"   -> MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK;
            default -> MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK;
        };
    }
}
