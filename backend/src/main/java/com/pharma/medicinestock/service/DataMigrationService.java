package com.pharma.medicinestock.service;

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
 * constraint to the new (user_id, medicine_id, medicine_stock_type) constraint that
 * supports the two-bucket medicine stock model.|
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataMigrationService {

    private final JdbcTemplate jdbc;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        dropMedicineStockTypeCheckConstraints();
        widenMedicineStockTypeColumns();
        setDefaultMedicineStockType();
        renameMedicineStockTypeValues();
        dropLegacyUniqueConstraint();
        createTransactionScreenshotsTable();
        widenQuantityColumnsToDecimal();
        dropDuplicateMedicineStockUniqueConstraint();
        addMissingForeignKeyIndexes();
        addTransactionHotPathIndex();
    }

    /**
     * The medicine_stock table ended up with two identical unique constraints on
     * (user_id, medicine_id, medicine_stock_type) — ddl-auto=update created a new
     * hash-named one during a past migration instead of recognizing the existing one as
     * matching. Harmless for correctness (either one enforces the same rule) but doubles
     * write overhead for zero benefit. Keeps the first (by name), drops the rest.
     */
    private void dropDuplicateMedicineStockUniqueConstraint() {
        try {
            List<String> names = jdbc.queryForList(
                "SELECT tc.constraint_name " +
                "FROM information_schema.table_constraints tc " +
                "JOIN information_schema.key_column_usage kcu " +
                "  ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema " +
                "WHERE tc.table_name = 'medicine_stock' AND tc.constraint_type = 'UNIQUE' " +
                "GROUP BY tc.constraint_name " +
                "HAVING COUNT(*) = 3 " +
                "  AND bool_and(kcu.column_name IN ('user_id', 'medicine_id', 'medicine_stock_type')) " +
                "ORDER BY tc.constraint_name",
                String.class);
            for (String name : names.subList(1, names.size())) {
                jdbc.execute("ALTER TABLE medicine_stock DROP CONSTRAINT \"" + name + "\"");
                log.info("DataMigration: dropped duplicate unique constraint '{}' on medicine_stock", name);
            }
        } catch (Exception e) {
            log.debug("DataMigration: duplicate unique constraint cleanup skipped — {}", e.getMessage());
        }
    }

    /**
     * Postgres does not auto-index foreign key columns. These two are read in real WHERE
     * clauses on the user-deletion path (nullifyApprovedBy / nullifyAdjustedBy), which
     * currently forces a full table scan on every user deletion; they also speed up the FK
     * constraint check Postgres does whenever a referenced user/medicine row is deleted.
     * Small tables today, but this is standard, low-cost insurance rather than a
     * measurable win right now — see also addTransactionHotPathIndex() for the one index
     * that actually matters at current scale.
     */
    private void addMissingForeignKeyIndexes() {
        record IndexDef(String name, String table, String column) {}
        List<IndexDef> indexes = List.of(
            new IndexDef("idx_tx_approved_by", "transactions", "approved_by"),
            new IndexDef("idx_adj_adjusted_by_id", "medicine_stock_adjustments", "adjusted_by_id"),
            new IndexDef("idx_ms_medicine_id", "medicine_stock", "medicine_id"),
            new IndexDef("idx_med_pharma_company_id", "medicines", "pharma_company_id")
        );
        for (IndexDef idx : indexes) {
            try {
                jdbc.execute("CREATE INDEX IF NOT EXISTS " + idx.name() + " ON " + idx.table() + "(" + idx.column() + ")");
                log.info("DataMigration: ensured index {} on {}({})", idx.name(), idx.table(), idx.column());
            } catch (Exception e) {
                log.debug("DataMigration: index {} skipped — {}", idx.name(), e.getMessage());
            }
        }
    }

    /**
     * CurrentStockCalculator.settledQuantity() runs findNonRejectedSubmittedUpToForUser on
     * every transaction submission — the one genuinely hot path in this app. That query
     * filters on (submitted_by, status, submitted_at) together; a composite index serves
     * the whole WHERE clause in one index scan instead of Postgres bitmap-AND-ing three
     * separate single-column indexes. idx_tx_submitted_by becomes redundant once this
     * exists (leftmost-prefix matching covers the same lookups) and is dropped to avoid
     * paying its write overhead twice for the same coverage.
     */
    private void addTransactionHotPathIndex() {
        try {
            jdbc.execute(
                "CREATE INDEX IF NOT EXISTS idx_tx_submitted_by_status_at " +
                "ON transactions(submitted_by, status, submitted_at)");
            log.info("DataMigration: ensured composite index idx_tx_submitted_by_status_at on transactions");
        } catch (Exception e) {
            log.debug("DataMigration: composite transaction index skipped — {}", e.getMessage());
        }
        try {
            jdbc.execute("DROP INDEX IF EXISTS idx_tx_submitted_by");
            log.info("DataMigration: dropped idx_tx_submitted_by (superseded by the composite index)");
        } catch (Exception e) {
            log.debug("DataMigration: idx_tx_submitted_by drop skipped — {}", e.getMessage());
        }
    }

    /**
     * Drop CHECK constraints that Hibernate 6 auto-generates for @Enumerated(EnumType.STRING) fields.
     * Hibernate 6 creates CHECK (medicine_stock_type IN ('REGULAR', 'ADMIN_STOCK')) using the original enum
     * names. These constraints block the rename UPDATEs that follow, causing them to fail silently
     * and leaving stale values that JPQL queries can't match.
     */
    private void dropMedicineStockTypeCheckConstraints() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT con.conname, rel.relname " +
                "FROM pg_constraint con " +
                "JOIN pg_class rel ON rel.oid = con.conrelid " +
                "JOIN pg_attribute att ON att.attrelid = rel.oid AND att.attnum = ANY(con.conkey) " +
                "WHERE rel.relname IN ('medicine_stock', 'transactions', 'medicine_stock_adjustments') " +
                "AND con.contype = 'c' AND att.attname = 'medicine_stock_type'");
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
     * Widen medicine_stock_type columns to VARCHAR(30) before renaming values.
     * REGULAR_MEDICINE_STOCK is 22 chars — any column narrower than that silently
     * drops the rename UPDATE (exception caught below), leaving stale values.
     *
     * The length check (skip when already >= 30) is required, not just an optimization:
     * re-running ALTER COLUMN TYPE on an already-VARCHAR(30) column still bumps Postgres's
     * relation cache-invalidation counter even though nothing changes, which breaks any
     * server-side prepared statement a pooled Hibernate connection already holds against that
     * table (seen as "cached plan must not change result type" on the next INSERT after seeding).
     */
    private void widenMedicineStockTypeColumns() {
        for (String table : List.of("medicine_stock", "transactions", "medicine_stock_adjustments")) {
            try {
                Integer len = jdbc.queryForObject(
                        "SELECT character_maximum_length FROM information_schema.columns " +
                        "WHERE table_name = ? AND column_name = 'medicine_stock_type'", Integer.class, table);
                if (len != null && len >= 30) {
                    continue; // already wide enough — skip to avoid invalidating cached statement plans
                }
                jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN medicine_stock_type TYPE VARCHAR(30)");
                log.info("DataMigration: widened {}.medicine_stock_type to VARCHAR(30)", table);
            } catch (Exception e) {
                log.debug("DataMigration: {}.medicine_stock_type widen skipped — {}", table, e.getMessage());
            }
        }
    }

    /**
     * Quantities are now tracked to one decimal place (BigDecimal), not whole units (Integer).
     * ddl-auto=validate in prod means Hibernate never alters existing columns, and ddl-auto=update
     * in dev has already proven unreliable for column-type changes in this codebase (see
     * widenMedicineStockTypeColumns() above) — so this checks the live column type on every startup
     * rather than relying on Hibernate. The scale check (skip when already 1) is required, not just
     * an optimization: re-running ALTER COLUMN TYPE on an already-NUMERIC(10,1) column still bumps
     * Postgres's relation cache-invalidation counter even though nothing changes, which breaks any
     * server-side prepared statement a pooled Hibernate connection already holds against that table
     * (seen as "cached plan must not change result type" on the next INSERT after seeding).
     */
    private void widenQuantityColumnsToDecimal() {
        for (String table : List.of("medicine_stock", "medicine_stock_adjustments", "transactions")) {
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

    /** Backfill existing rows that have no medicine_stock_type yet (pre-rename default). */
    private void setDefaultMedicineStockType() {
        try {
            int n = jdbc.update(
                "UPDATE medicine_stock SET medicine_stock_type = 'REGULAR_MEDICINE_STOCK' WHERE medicine_stock_type IS NULL");
            if (n > 0) log.info("DataMigration: set {} rows to REGULAR_MEDICINE_STOCK medicine_stock_type", n);
        } catch (Exception e) {
            log.debug("DataMigration: medicine_stock_type backfill skipped — {}", e.getMessage());
        }
    }

    /** Rename old enum string values to the new names in all tables that store medicine_stock_type. */
    private void renameMedicineStockTypeValues() {
        try {
            int n1 = jdbc.update(
                "UPDATE medicine_stock SET medicine_stock_type = 'REGULAR_MEDICINE_STOCK' WHERE medicine_stock_type = 'REGULAR'");
            int n2 = jdbc.update(
                "UPDATE medicine_stock SET medicine_stock_type = 'ADMIN_MEDICINE_STOCK' WHERE medicine_stock_type = 'ADMIN_STOCK'");
            if (n1 + n2 > 0)
                log.info("DataMigration: renamed medicineStock medicine_stock_type values (regular={}, admin={})", n1, n2);
        } catch (Exception e) {
            log.debug("DataMigration: medicineStock medicine_stock_type rename skipped — {}", e.getMessage());
        }
        try {
            int n3 = jdbc.update(
                "UPDATE transactions SET medicine_stock_type = 'REGULAR_MEDICINE_STOCK' WHERE medicine_stock_type = 'REGULAR'");
            int n4 = jdbc.update(
                "UPDATE transactions SET medicine_stock_type = 'ADMIN_MEDICINE_STOCK' WHERE medicine_stock_type = 'ADMIN_STOCK'");
            if (n3 + n4 > 0)
                log.info("DataMigration: renamed transactions medicine_stock_type values (regular={}, admin={})", n3, n4);
        } catch (Exception e) {
            log.debug("DataMigration: transactions medicine_stock_type rename skipped — {}", e.getMessage());
        }
        try {
            int n5 = jdbc.update(
                "UPDATE medicine_stock_adjustments SET medicine_stock_type = 'REGULAR_MEDICINE_STOCK' WHERE medicine_stock_type = 'REGULAR'");
            int n6 = jdbc.update(
                "UPDATE medicine_stock_adjustments SET medicine_stock_type = 'ADMIN_MEDICINE_STOCK' WHERE medicine_stock_type = 'ADMIN_STOCK'");
            if (n5 + n6 > 0)
                log.info("DataMigration: renamed medicine_stock_adjustments.medicine_stock_type values (regular={}, admin={})", n5, n6);
        } catch (Exception e) {
            log.debug("DataMigration: medicine_stock_adjustments.medicine_stock_type rename skipped — {}", e.getMessage());
        }
    }

    /**
     * Drop the old two-column unique constraint (user_id, medicine_id) so that
     * a user can now have both REGULAR_MEDICINE_STOCK and ADMIN_MEDICINE_STOCK rows for the same medicine.
     * Uses information_schema for portability.
     */
    private void dropLegacyUniqueConstraint() {
        try {
            // Find unique constraints on 'medicine_stock' covering exactly {user_id, medicine_id}
            List<String> names = jdbc.queryForList(
                "SELECT tc.constraint_name " +
                "FROM information_schema.table_constraints tc " +
                "JOIN information_schema.key_column_usage kcu " +
                "  ON tc.constraint_name = kcu.constraint_name " +
                "  AND tc.table_schema = kcu.table_schema " +
                "WHERE tc.table_name = 'medicine_stock' AND tc.constraint_type = 'UNIQUE' " +
                "GROUP BY tc.constraint_name " +
                "HAVING COUNT(*) = 2 " +
                "  AND bool_and(kcu.column_name IN ('user_id', 'medicine_id'))",
                String.class);
            for (String name : names) {
                jdbc.execute("ALTER TABLE medicine_stock DROP CONSTRAINT \"" + name + "\"");
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
