package com.pharma.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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
        dropInventoryTypeCheckConstraints();
        widenInventoryTypeColumns();
        setDefaultInventoryType();
        renameInventoryTypeValues();
        dropLegacyUniqueConstraint();
        createTransactionScreenshotsTable();
        widenQuantityColumnsToDecimal();
    }

    /**
     * Drop CHECK constraints that Hibernate 6 auto-generates for @Enumerated(EnumType.STRING) fields.
     * Hibernate 6 creates CHECK (inventory_type IN ('REGULAR', 'ADMIN_STOCK')) using the original enum
     * names. These constraints block the rename UPDATEs that follow, causing them to fail silently
     * and leaving stale values that JPQL queries can't match.
     */
    private void dropInventoryTypeCheckConstraints() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT con.conname, rel.relname " +
                "FROM pg_constraint con " +
                "JOIN pg_class rel ON rel.oid = con.conrelid " +
                "JOIN pg_attribute att ON att.attrelid = rel.oid AND att.attnum = ANY(con.conkey) " +
                "WHERE rel.relname IN ('inventory', 'transactions', 'inventory_adjustments') " +
                "AND con.contype = 'c' AND att.attname = 'inventory_type'");
            for (Map<String, Object> row : rows) {
                String table = (String) row.get("relname");
                String constraint = (String) row.get("conname");
                jdbc.execute("ALTER TABLE \"" + table + "\" DROP CONSTRAINT IF EXISTS \"" + constraint + "\"");
                log.info("DataMigration: dropped check constraint '{}' on {}", constraint, table);
            }
        } catch (Exception e) {
            log.debug("DataMigration: check constraint drop skipped — {}", e.getMessage());
        }
    }

    /**
     * Widen inventory_type columns to VARCHAR(30) before renaming values.
     * REGULAR_MEDICINE_STOCK is 22 chars — any column narrower than that silently
     * drops the rename UPDATE (exception caught below), leaving stale values.
     *
     * The length check (skip when already >= 30) is required, not just an optimization:
     * re-running ALTER COLUMN TYPE on an already-VARCHAR(30) column still bumps Postgres's
     * relation cache-invalidation counter even though nothing changes, which breaks any
     * server-side prepared statement a pooled Hibernate connection already holds against that
     * table (seen as "cached plan must not change result type" on the next INSERT after seeding).
     */
    private void widenInventoryTypeColumns() {
        for (String table : List.of("inventory", "transactions", "inventory_adjustments")) {
            try {
                Integer len = jdbc.queryForObject(
                        "SELECT character_maximum_length FROM information_schema.columns " +
                        "WHERE table_name = ? AND column_name = 'inventory_type'", Integer.class, table);
                if (len != null && len >= 30) {
                    continue; // already wide enough — skip to avoid invalidating cached statement plans
                }
                jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN inventory_type TYPE VARCHAR(30)");
                log.info("DataMigration: widened {}.inventory_type to VARCHAR(30)", table);
            } catch (Exception e) {
                log.debug("DataMigration: {}.inventory_type widen skipped — {}", table, e.getMessage());
            }
        }
    }

    /**
     * Quantities are now tracked to one decimal place (BigDecimal), not whole units (Integer).
     * ddl-auto=validate in prod means Hibernate never alters existing columns, and ddl-auto=update
     * in dev has already proven unreliable for column-type changes in this codebase (see
     * widenInventoryTypeColumns() above) — so this checks the live column type on every startup
     * rather than relying on Hibernate. The scale check (skip when already 1) is required, not just
     * an optimization: re-running ALTER COLUMN TYPE on an already-NUMERIC(10,1) column still bumps
     * Postgres's relation cache-invalidation counter even though nothing changes, which breaks any
     * server-side prepared statement a pooled Hibernate connection already holds against that table
     * (seen as "cached plan must not change result type" on the next INSERT after seeding).
     */
    private void widenQuantityColumnsToDecimal() {
        for (String table : List.of("inventory", "inventory_adjustments", "transactions")) {
            try {
                Integer scale = jdbc.queryForObject(
                        "SELECT numeric_scale FROM information_schema.columns " +
                        "WHERE table_name = ? AND column_name = 'quantity'", Integer.class, table);
                if (scale != null && scale == 1) {
                    continue; // already NUMERIC(10,1) — skip to avoid invalidating cached statement plans
                }
                jdbc.execute("ALTER TABLE " + table +
                        " ALTER COLUMN quantity TYPE NUMERIC(10,1) USING quantity::numeric(10,1)");
                log.info("DataMigration: widened {}.quantity to NUMERIC(10,1)", table);
            } catch (Exception e) {
                log.debug("DataMigration: {}.quantity widen skipped — {}", table, e.getMessage());
            }
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

}
