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
        setDefaultInventoryType();
        dropLegacyUniqueConstraint();
        removeAdminInventory();
    }

    /** Backfill existing rows that have no inventory_type yet. */
    private void setDefaultInventoryType() {
        try {
            int n = jdbc.update(
                "UPDATE inventory SET inventory_type = 'REGULAR' WHERE inventory_type IS NULL");
            if (n > 0) log.info("DataMigration: set {} rows to REGULAR inventory_type", n);
        } catch (Exception e) {
            log.debug("DataMigration: inventory_type backfill skipped — {}", e.getMessage());
        }
    }

    /**
     * Drop the old two-column unique constraint (user_id, medicine_id) so that
     * a user can now have both REGULAR and ADMIN_STOCK rows for the same medicine.
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
