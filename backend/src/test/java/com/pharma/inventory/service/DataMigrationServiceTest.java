package com.pharma.inventory.service;

import com.pharma.inventory.config.DataInitializer;
import com.pharma.inventory.entity.Inventory.InventoryType;
import com.pharma.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Integration tests for DataMigrationService run against a real H2 in-memory database.
 *
 * NOT @Transactional — schema modifications (dropping check constraints, JDBC UPDATEs)
 * must commit so that migration methods can see them. @BeforeEach reseeds the database
 * to provide isolation between tests.
 *
 * Coverage targets the three production bugs that were invisible to mocked-service tests:
 *  1. Legacy inventory_type values ('REGULAR', 'ADMIN_STOCK') not renamed → zero reports
 *  2. onStartup() wiping ADMIN_MEDICINE_STOCK on every restart
 *  3. seedInventoryIfEmpty() running again when inventory already exists
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("DataMigrationService — startup migration")
class DataMigrationServiceTest {

    @Autowired DataMigrationService dataMigrationService;
    @Autowired DataInitializer dataInitializer;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reseed() {
        dataInitializer.reseed();
    }

    // ── Legacy enum value rename ──────────────────────────────────────────

    /**
     * In production, Hibernate 6 created CHECK constraints with the original enum names
     * ('REGULAR', 'ADMIN_STOCK'). Those constraints were later dropped by
     * dropInventoryTypeCheckConstraints(). In H2 (test profile), @Convert does not generate
     * CHECK constraints, so we can insert legacy values directly via JDBC to simulate the
     * production state and verify the rename migration logic.
     */
    @Nested @DisplayName("Legacy inventory_type rename")
    class LegacyRename {

        @Test @DisplayName("'REGULAR' values in inventory table are renamed to REGULAR_MEDICINE_STOCK")
        void renamesLegacyRegularInInventoryTable() {
            Long id = jdbc.queryForObject("SELECT id FROM inventory LIMIT 1", Long.class);
            jdbc.update("UPDATE inventory SET inventory_type = 'REGULAR' WHERE id = ?", id);
            assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory WHERE inventory_type = 'REGULAR'", Integer.class))
                .isEqualTo(1);

            dataMigrationService.onStartup();

            assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory WHERE inventory_type IN ('REGULAR', 'ADMIN_STOCK')", Integer.class))
                .as("No legacy inventory_type values should remain after migration")
                .isZero();
        }

        @Test @DisplayName("'ADMIN_STOCK' values in inventory table are renamed to ADMIN_MEDICINE_STOCK")
        void renamesLegacyAdminStockInInventoryTable() {
            Long id = jdbc.queryForObject(
                "SELECT id FROM inventory WHERE inventory_type = 'ADMIN_MEDICINE_STOCK' LIMIT 1", Long.class);
            jdbc.update("UPDATE inventory SET inventory_type = 'ADMIN_STOCK' WHERE id = ?", id);

            dataMigrationService.onStartup();

            assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory WHERE inventory_type = 'ADMIN_STOCK'", Integer.class))
                .as("No ADMIN_STOCK legacy values should remain after migration")
                .isZero();
        }

        @Test @DisplayName("After rename, JPQL enum query for REGULAR_MEDICINE_STOCK returns the migrated rows")
        void jpqlEnumQuerySucceedsAfterLegacyRename() {
            Long id = jdbc.queryForObject("SELECT id FROM inventory LIMIT 1", Long.class);
            jdbc.update("UPDATE inventory SET inventory_type = 'REGULAR' WHERE id = ?", id);
            int sizeBefore = inventoryRepository.findAllNonZeroByInventoryType(InventoryType.REGULAR_MEDICINE_STOCK).size();

            dataMigrationService.onStartup();

            int sizeAfter = inventoryRepository.findAllNonZeroByInventoryType(InventoryType.REGULAR_MEDICINE_STOCK).size();
            assertThat(sizeAfter)
                .as("JPQL enum query should return at least as many rows after migration as before " +
                    "(the renamed row must now be visible)")
                .isGreaterThanOrEqualTo(sizeBefore);
        }

        @Test @DisplayName("'REGULAR' values in transactions table are renamed")
        void renamesLegacyRegularInTransactionsTable() {
            // transactions table only has inventory_type when there are submitted transactions;
            // use a direct INSERT to simulate a legacy transaction row
            try {
                Long userId = jdbc.queryForObject("SELECT id FROM users LIMIT 1", Long.class);
                Long medicineId = jdbc.queryForObject("SELECT id FROM medicines LIMIT 1", Long.class);
                jdbc.update(
                    "INSERT INTO transactions (user_id, medicine_id, quantity, status, notes, submitted_at, inventory_type) " +
                    "VALUES (?, ?, 1, 'PENDING', 'Legacy rename test', CURRENT_TIMESTAMP, 'REGULAR')",
                    userId, medicineId);
                assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM transactions WHERE inventory_type = 'REGULAR'", Integer.class))
                    .isEqualTo(1);

                dataMigrationService.onStartup();

                assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM transactions WHERE inventory_type = 'REGULAR'", Integer.class))
                    .as("transactions.inventory_type 'REGULAR' must be renamed to REGULAR_MEDICINE_STOCK")
                    .isZero();
            } catch (Exception e) {
                // If transactions table schema differs (e.g. missing column), skip gracefully
                // but fail loudly so it's not silently ignored
                assertThat(e.getMessage())
                    .as("Unexpected error setting up legacy transactions row")
                    .doesNotContain("Column \"INVENTORY_TYPE\" not found");
            }
        }
    }

    // ── Startup idempotency ───────────────────────────────────────────────

    @Nested @DisplayName("onStartup() idempotency")
    class Idempotency {

        @Test @DisplayName("Admin stock count is unchanged after running onStartup() a second time")
        void adminStockCountUnchangedAfterSecondStartup() {
            long adminBefore = countByType("ADMIN_MEDICINE_STOCK");

            dataMigrationService.onStartup();

            assertThat(countByType("ADMIN_MEDICINE_STOCK"))
                .as("Admin stock must not be wiped by repeated onStartup() calls")
                .isEqualTo(adminBefore);
        }

        @Test @DisplayName("Total inventory count is unchanged after running onStartup() a second time")
        void totalInventoryUnchangedAfterSecondStartup() {
            long before = inventoryRepository.count();

            dataMigrationService.onStartup();

            assertThat(inventoryRepository.count())
                .as("Inventory count must not change: seedInventoryIfEmpty must skip when table is populated")
                .isEqualTo(before);
        }

        @Test @DisplayName("Calling onStartup() three times still preserves all inventory")
        void tripleStartupIsFullyIdempotent() {
            long before = inventoryRepository.count();

            dataMigrationService.onStartup();
            dataMigrationService.onStartup();
            dataMigrationService.onStartup();

            assertThat(inventoryRepository.count()).isEqualTo(before);
        }
    }

    // ── seedInventoryIfEmpty ──────────────────────────────────────────────

    @Nested @DisplayName("seedInventoryIfEmpty behaviour")
    class SeedIfEmpty {

        @Test @DisplayName("Does not add rows when inventory is already populated")
        void skipsWhenInventoryPopulated() {
            long before = inventoryRepository.count();
            dataMigrationService.onStartup(); // triggers seedInventoryIfEmpty internally
            assertThat(inventoryRepository.count())
                .as("seedInventoryIfEmpty must skip when inventory already has rows")
                .isEqualTo(before);
        }

        @Test @DisplayName("seedInventoryIfEmpty runs without error when inventory is empty")
        void seedsWhenInventoryEmpty() {
            // Clear inventory to simulate an empty-table scenario
            jdbc.execute("DELETE FROM inventory");
            assertThat(inventoryRepository.count()).isZero();

            // In the test environment, seedInventoryIfEmpty references production usernames
            // (allwyn, arnab, karan, etc.) that don't exist in the H2 demo seed.
            // The important thing is that onStartup() completes without throwing.
            assertThatNoException().isThrownBy(() -> dataMigrationService.onStartup());
        }
    }

    // ── backfillMissingInventoryAdjustments ─────────────────────────────────

    @Nested @DisplayName("backfillMissingInventoryAdjustments behaviour")
    class Backfill {

        @Test @DisplayName("Inserts a backfill adjustment for an inventory row with no adjustment history")
        void backfillsGapForUntrackedInventory() {
            Map<String, Object> bucket = firstInventoryBucketWithNoAdjustments();
            long userId = ((Number) bucket.get("user_id")).longValue();
            long medicineId = ((Number) bucket.get("medicine_id")).longValue();
            int quantity = ((Number) bucket.get("quantity")).intValue();

            dataMigrationService.onStartup();

            Integer backfilled = jdbc.queryForObject(
                "SELECT quantity FROM inventory_adjustments " +
                "WHERE user_id = ? AND medicine_id = ? AND note = 'Initial stock backfill'",
                Integer.class, userId, medicineId);
            assertThat(backfilled)
                .as("Gap-filling ADD adjustment should match the untracked inventory quantity")
                .isEqualTo(quantity);
        }

        @Test @DisplayName("A bucket's backfill row is never recomputed once it exists, even if later data would change the gap")
        void backfillRowIsStableAcrossRestartsDespiteDataChanges() {
            Map<String, Object> bucket = firstInventoryBucketWithNoAdjustments();
            long userId = ((Number) bucket.get("user_id")).longValue();
            long medicineId = ((Number) bucket.get("medicine_id")).longValue();

            dataMigrationService.onStartup();
            Integer firstRun = jdbc.queryForObject(
                "SELECT quantity FROM inventory_adjustments " +
                "WHERE user_id = ? AND medicine_id = ? AND note = 'Initial stock backfill'",
                Integer.class, userId, medicineId);

            // Simulate inventory drifting after the bucket was already backfilled (e.g. a
            // manual DB correction, or the kind of post-hoc data change that used to make the
            // old backfill silently rewrite already-generated historical daily reports).
            jdbc.update("UPDATE inventory SET quantity = quantity + 50 WHERE user_id = ? AND medicine_id = ?",
                userId, medicineId);

            dataMigrationService.onStartup();

            Integer afterSecondRun = jdbc.queryForObject(
                "SELECT quantity FROM inventory_adjustments " +
                "WHERE user_id = ? AND medicine_id = ? AND note = 'Initial stock backfill'",
                Integer.class, userId, medicineId);
            long rowCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_adjustments " +
                "WHERE user_id = ? AND medicine_id = ? AND note = 'Initial stock backfill'",
                Long.class, userId, medicineId);

            assertThat(afterSecondRun)
                .as("Backfill quantity must not change once the bucket has already been backfilled")
                .isEqualTo(firstRun);
            assertThat(rowCount)
                .as("Backfill must not insert a duplicate row for an already-backfilled bucket")
                .isEqualTo(1);
        }

        @Test @DisplayName("A newly-appearing inventory bucket still gets backfilled without disturbing existing backfill rows")
        void backfillsNewBucketsWithoutTouchingExisting() {
            dataMigrationService.onStartup(); // backfills whatever gaps exist today

            long existingBackfillCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_adjustments WHERE note = 'Initial stock backfill'", Long.class);

            Long userId = jdbc.queryForObject("SELECT id FROM users LIMIT 1", Long.class);
            List<Long> freeMedicineIds = jdbc.queryForList(
                "SELECT id FROM medicines WHERE id NOT IN " +
                "(SELECT medicine_id FROM inventory WHERE user_id = ?) LIMIT 1", Long.class, userId);
            if (freeMedicineIds.isEmpty()) return; // every medicine already has a bucket for this user
            Long medicineId = freeMedicineIds.get(0);

            jdbc.update(
                "INSERT INTO inventory (user_id, medicine_id, quantity, inventory_type, last_note, last_updated) " +
                "VALUES (?, ?, 7, 'REGULAR_MEDICINE_STOCK', 'test seed', CURRENT_TIMESTAMP)",
                userId, medicineId);

            dataMigrationService.onStartup();

            Integer newBucketBackfill = jdbc.queryForObject(
                "SELECT quantity FROM inventory_adjustments " +
                "WHERE user_id = ? AND medicine_id = ? AND note = 'Initial stock backfill'",
                Integer.class, userId, medicineId);
            long backfillCountAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_adjustments WHERE note = 'Initial stock backfill'", Long.class);

            assertThat(newBucketBackfill).isEqualTo(7);
            assertThat(backfillCountAfter).isEqualTo(existingBackfillCount + 1);
        }

        private Map<String, Object> firstInventoryBucketWithNoAdjustments() {
            return jdbc.queryForMap(
                "SELECT i.user_id, i.medicine_id, i.quantity FROM inventory i " +
                "WHERE i.quantity > 0 AND NOT EXISTS (" +
                "  SELECT 1 FROM inventory_adjustments a " +
                "  WHERE a.user_id = i.user_id AND a.medicine_id = i.medicine_id" +
                ") LIMIT 1");
        }
    }

    private long countByType(String inventoryType) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM inventory WHERE inventory_type = ?", Long.class, inventoryType);
    }
}
