package com.pharma.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs one-time schema and data migrations at application startup.
 *
 * Hibernate's ddl-auto=update cannot drop existing unique constraints, so this
 * service handles the migration from the old (user_id, medicine_id) unique
 * constraint to the new (user_id, medicine_id, inventory_type) constraint that
 * supports the two-bucket inventory model.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataMigrationService {

    private final JdbcTemplate jdbc;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        widenInventoryTypeColumns();
        setDefaultInventoryType();
        renameInventoryTypeValues();
        dropLegacyUniqueConstraint();
        removeAdminInventory();
        createTransactionScreenshotsTable();
    }

    /**
     * Widen inventory_type columns to VARCHAR(30) before renaming values.
     * The old column was VARCHAR(20), which is too short for REGULAR_MEDICINE_STOCK (22 chars).
     */
    private void widenInventoryTypeColumns() {
        try {
            jdbc.execute("ALTER TABLE transactions ALTER COLUMN inventory_type TYPE VARCHAR(30)");
            log.info("DataMigration: widened transactions.inventory_type to VARCHAR(30)");
        } catch (Exception e) {
            log.debug("DataMigration: transactions column widen skipped — {}", e.getMessage());
        }
        try {
            jdbc.execute("ALTER TABLE inventory_adjustments ALTER COLUMN inventory_type TYPE VARCHAR(30)");
            log.info("DataMigration: widened inventory_adjustments.inventory_type to VARCHAR(30)");
        } catch (Exception e) {
            log.debug("DataMigration: inventory_adjustments column widen skipped — {}", e.getMessage());
        }
    }

    /** Backfill existing rows that have no inventory_type yet (pre-rename default). */
    private void setDefaultInventoryType() {
        try {
            int n = jdbc.update(
                "UPDATE inventory SET inventory_type = 'REGULAR_MEDICINE_STOCK' WHERE inventory_type IS NULL");
            if (n > 0) log.info("DataMigration: set {} rows to REGULAR_MEDICINE_STOCK inventory_type", n);
        } catch (Exception e) {
            log.debug("DataMigration: inventory_type backfill skipped — {}", e.getMessage());
        }
    }

    /** Rename old enum string values to the new names in all tables that store inventory_type. */
    private void renameInventoryTypeValues() {
        try {
            int n1 = jdbc.update(
                "UPDATE inventory SET inventory_type = 'REGULAR_MEDICINE_STOCK' WHERE inventory_type = 'REGULAR'");
            int n2 = jdbc.update(
                "UPDATE inventory SET inventory_type = 'ADMIN_MEDICINE_STOCK' WHERE inventory_type = 'ADMIN_STOCK'");
            if (n1 + n2 > 0)
                log.info("DataMigration: renamed inventory inventory_type values (regular={}, admin={})", n1, n2);
        } catch (Exception e) {
            log.debug("DataMigration: inventory inventory_type rename skipped — {}", e.getMessage());
        }
        try {
            int n3 = jdbc.update(
                "UPDATE transactions SET inventory_type = 'REGULAR_MEDICINE_STOCK' WHERE inventory_type = 'REGULAR'");
            int n4 = jdbc.update(
                "UPDATE transactions SET inventory_type = 'ADMIN_MEDICINE_STOCK' WHERE inventory_type = 'ADMIN_STOCK'");
            if (n3 + n4 > 0)
                log.info("DataMigration: renamed transactions inventory_type values (regular={}, admin={})", n3, n4);
        } catch (Exception e) {
            log.debug("DataMigration: transactions inventory_type rename skipped — {}", e.getMessage());
        }
        try {
            int n5 = jdbc.update(
                "UPDATE inventory_adjustments SET inventory_type = 'REGULAR_MEDICINE_STOCK' WHERE inventory_type = 'REGULAR'");
            int n6 = jdbc.update(
                "UPDATE inventory_adjustments SET inventory_type = 'ADMIN_MEDICINE_STOCK' WHERE inventory_type = 'ADMIN_STOCK'");
            if (n5 + n6 > 0)
                log.info("DataMigration: renamed inventory_adjustments inventory_type values (regular={}, admin={})", n5, n6);
        } catch (Exception e) {
            log.debug("DataMigration: inventory_adjustments inventory_type rename skipped — {}", e.getMessage());
        }
    }

    /**
     * Drop the old two-column unique constraint (user_id, medicine_id) so that
     * a user can now have both REGULAR_MEDICINE_STOCK and ADMIN_MEDICINE_STOCK rows for the same medicine.
     * Uses information_schema for portability.
     */
    private void dropLegacyUniqueConstraint() {
        try {
            // Find unique constraints on 'inventory' covering exactly {user_id, medicine_id}
            List<String> names = jdbc.queryForList(
                "SELECT tc.constraint_name " +
                "FROM information_schema.table_constraints tc " +
                "JOIN information_schema.key_column_usage kcu " +
                "  ON tc.constraint_name = kcu.constraint_name " +
                "  AND tc.table_schema = kcu.table_schema " +
                "WHERE tc.table_name = 'inventory' AND tc.constraint_type = 'UNIQUE' " +
                "GROUP BY tc.constraint_name " +
                "HAVING COUNT(*) = 2 " +
                "  AND bool_and(kcu.column_name IN ('user_id', 'medicine_id'))",
                String.class);
            for (String name : names) {
                jdbc.execute("ALTER TABLE inventory DROP CONSTRAINT \"" + name + "\"");
                log.info("DataMigration: dropped legacy unique constraint '{}'", name);
            }
        } catch (Exception e) {
            log.debug("DataMigration: constraint drop skipped — {}", e.getMessage());
        }
    }

    /** Create the transaction_screenshots table for prod where ddl-auto=validate won't create it. */
    private void createTransactionScreenshotsTable() {
        try {
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS transaction_screenshots (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "transaction_id BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE, " +
                "data TEXT NOT NULL, " +
                "mime_type VARCHAR(50) NOT NULL, " +
                "display_order INT NOT NULL DEFAULT 0)"
            );
            log.info("DataMigration: transaction_screenshots table ensured");
        } catch (Exception e) {
            log.debug("DataMigration: transaction_screenshots table skipped — {}", e.getMessage());
        }
    }

    /** Remove any inventory rows that belong to the admin user (admin has no inventory). */
    private void removeAdminInventory() {
        try {
            int n = jdbc.update(
                "DELETE FROM inventory WHERE user_id IN " +
                "(SELECT id FROM users WHERE role = 'ADMIN')");
            if (n > 0) log.info("DataMigration: removed {} admin inventory rows", n);
        } catch (Exception e) {
            log.debug("DataMigration: admin inventory removal skipped — {}", e.getMessage());
        }
    }
}
