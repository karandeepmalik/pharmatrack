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
        seedInventoryIfEmpty();
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
     */
    private void widenInventoryTypeColumns() {
        for (String table : List.of("inventory", "transactions", "inventory_adjustments")) {
            try {
                jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN inventory_type TYPE VARCHAR(30)");
                log.info("DataMigration: widened {}.inventory_type to VARCHAR(30)", table);
            } catch (Exception e) {
                log.debug("DataMigration: {}.inventory_type widen skipped — {}", table, e.getMessage());
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

    /**
     * Seeds the inventory table when it is completely empty (e.g. after a production reseed
     * that cleared all data). Only inserts rows for users/medicines that already exist.
     * Uses ON CONFLICT DO NOTHING so it is safe to re-run.
     */
    private void seedInventoryIfEmpty() {
        try {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM inventory", Integer.class);
            if (count != null && count > 0) return;
            log.info("DataMigration: inventory table is empty — seeding initial stock");

            String sql =
                "INSERT INTO inventory (user_id, medicine_id, quantity, inventory_type, last_note, last_updated) " +
                "SELECT u.id, m.id, ?, ?, 'Initial stock', NOW() " +
                "FROM users u JOIN medicines m ON true " +
                "WHERE u.username = ? AND m.name = ? " +
                "ON CONFLICT (user_id, medicine_id, inventory_type) DO NOTHING";

            // Current stock is derived by forward-reconstructing inventory_adjustments +
            // transactions (see CurrentStockCalculator), not by trusting inventory.quantity
            // directly — so every seeded row needs a matching genesis adjustment or it's
            // invisible to that reconstruction (shows 0 available until an admin touches it).
            String findIdsSql =
                "SELECT u.id AS user_id, m.id AS medicine_id " +
                "FROM users u JOIN medicines m ON true " +
                "WHERE u.username = ? AND m.name = ?";
            String adjSql =
                "INSERT INTO inventory_adjustments " +
                "(user_id, medicine_id, quantity, adjustment_type, note, internal_movement, " +
                " in_transit, was_in_transit, transit_days, inventory_type, adjusted_at) " +
                "VALUES (?, ?, ?, 'ADD', 'Initial stock', false, false, false, 2, ?, NOW())";

            String REG   = "REGULAR_MEDICINE_STOCK";
            String ADMIN = "ADMIN_MEDICINE_STOCK";

            // ── Vial 10 ml — regular stock ───────────────────────────────
            Object[][] vial10 = {
                {12, REG,   "allwyn",  "Shield FX Vial 10 ml"},
                { 2, REG,   "arnab",   "Shield FX Vial 10 ml"},
                { 5, REG,   "atif",    "Shield FX Vial 10 ml"},
                { 9, REG,   "farheen", "Shield FX Vial 10 ml"},
                { 3, REG,   "karan",   "Shield FX Vial 10 ml"},
                { 2, REG,   "riona",   "Shield FX Vial 10 ml"},
            };
            // ── Vial 5 ml — regular stock ────────────────────────────────
            Object[][] vial5 = {
                { 6, REG,   "allwyn",   "Shield FX Vial 5 ml"},
                {10, REG,   "anubhuti", "Shield FX Vial 5 ml"},
                { 2, REG,   "arnab",    "Shield FX Vial 5 ml"},
                { 1, REG,   "atif",     "Shield FX Vial 5 ml"},
                {11, REG,   "dhairya",  "Shield FX Vial 5 ml"},
                {16, REG,   "farheen",  "Shield FX Vial 5 ml"},
                {87, REG,   "karan",    "Shield FX Vial 5 ml"},
                { 8, REG,   "riona",    "Shield FX Vial 5 ml"},
                { 6, REG,   "swati",    "Shield FX Vial 5 ml"},
            };
            // ── Tablet 50 mg — regular stock ─────────────────────────────
            Object[][] tab50 = {
                {2, REG, "farheen", "Shield FX Tablet 50 mg (10 Tablets)"},
                {2, REG, "karan",   "Shield FX Tablet 50 mg (10 Tablets)"},
                {1, REG, "riona",   "Shield FX Tablet 50 mg (10 Tablets)"},
            };
            // ── Tablet 25 mg — regular stock ─────────────────────────────
            Object[][] tab25 = {
                {4, REG, "dhairya", "Shield FX Tablet 25 mg (10 Tablets)"},
                {5, REG, "farheen", "Shield FX Tablet 25 mg (10 Tablets)"},
            };
            // ── Tablet 12 mg — regular stock ─────────────────────────────
            Object[][] tab12reg = {
                {10, REG, "dhairya", "Shield FX Tablet 12 mg (10 Tablets)"},
                {25, REG, "karan",   "Shield FX Tablet 12 mg (10 Tablets)"},
                { 5, REG, "riona",   "Shield FX Tablet 12 mg (10 Tablets)"},
            };
            // ── Tablet 12 mg — admin stock ────────────────────────────────
            Object[][] tab12adm = {
                {10, ADMIN, "arnab", "Shield FX Tablet 12 mg (10 Tablets)"},
                {26, ADMIN, "karan", "Shield FX Tablet 12 mg (10 Tablets)"},
            };

            int inserted = 0;
            for (Object[][] group : new Object[][][]{vial10, vial5, tab50, tab25, tab12reg, tab12adm}) {
                for (Object[] row : group) {
                    int qty = (Integer) row[0];
                    String type = (String) row[1];
                    String username = (String) row[2];
                    String medicineName = (String) row[3];
                    int n = jdbc.update(sql, qty, type, username, medicineName);
                    if (n > 0) {
                        inserted += n;
                        Map<String, Object> ids = jdbc.queryForMap(findIdsSql, username, medicineName);
                        jdbc.update(adjSql, ids.get("user_id"), ids.get("medicine_id"), qty, type);
                    }
                }
            }
            log.info("DataMigration: seeded {} inventory rows with matching genesis adjustments", inserted);
        } catch (Exception e) {
            log.warn("DataMigration: inventory seed failed — {}", e.getMessage());
        }
    }

}
