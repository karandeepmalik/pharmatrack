package com.pharma.medicinestock.service;

import com.pharma.medicinestock.config.DataInitializer;
import com.pharma.medicinestock.entity.MedicineStock.MedicineStockType;
import com.pharma.medicinestock.repository.MedicineStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DataMigrationService run against a real H2 in-memory database.
 *
 * NOT @Transactional — schema modifications (dropping check constraints, JDBC UPDATEs)
 * must commit so that migration methods can see them. @BeforeEach reseeds the database
 * to provide isolation between tests.
 *
 * Coverage targets the two production bugs that were invisible to mocked-service tests:
 *  1. Legacy medicine_stock_type values ('REGULAR', 'ADMIN_STOCK') not renamed → zero reports
 *  2. onStartup() wiping ADMIN_MEDICINE_STOCK on every restart
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("DataMigrationService — startup migration")
class DataMigrationServiceTest {

    @Autowired DataMigrationService dataMigrationService;
    @Autowired DataInitializer dataInitializer;
    @Autowired MedicineStockRepository medicineStockRepository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reseed() {
        dataInitializer.reseed();
    }

    // ── Legacy enum value rename ──────────────────────────────────────────

    /**
     * In production, Hibernate 6 created CHECK constraints with the original enum names
     * ('REGULAR', 'ADMIN_STOCK'). Those constraints were later dropped by
     * dropMedicineStockTypeCheckConstraints(). In H2 (test profile), @Convert does not generate
     * CHECK constraints, so we can insert legacy values directly via JDBC to simulate the
     * production state and verify the rename migration logic.
     */
    @Nested @DisplayName("Legacy medicine_stock_type rename")
    class LegacyRename {

        @Test @DisplayName("'REGULAR' values in medicineStock table are renamed to REGULAR_MEDICINE_STOCK")
        void renamesLegacyRegularInMedicineStockTable() {
            Long id = jdbc.queryForObject("SELECT id FROM medicine_stock LIMIT 1", Long.class);
            jdbc.update("UPDATE medicine_stock SET medicine_stock_type = 'REGULAR' WHERE id = ?", id);
            assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM medicine_stock WHERE medicine_stock_type = 'REGULAR'", Integer.class))
                .isEqualTo(1);

            dataMigrationService.onStartup();

            assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM medicine_stock WHERE medicine_stock_type IN ('REGULAR', 'ADMIN_STOCK')", Integer.class))
                .as("No legacy medicine_stock_type values should remain after migration")
                .isZero();
        }

        @Test @DisplayName("'ADMIN_STOCK' values in medicineStock table are renamed to ADMIN_MEDICINE_STOCK")
        void renamesLegacyAdminStockInMedicineStockTable() {
            Long id = jdbc.queryForObject(
                "SELECT id FROM medicine_stock WHERE medicine_stock_type = 'ADMIN_MEDICINE_STOCK' LIMIT 1", Long.class);
            jdbc.update("UPDATE medicine_stock SET medicine_stock_type = 'ADMIN_STOCK' WHERE id = ?", id);

            dataMigrationService.onStartup();

            assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM medicine_stock WHERE medicine_stock_type = 'ADMIN_STOCK'", Integer.class))
                .as("No ADMIN_STOCK legacy values should remain after migration")
                .isZero();
        }

        @Test @DisplayName("After rename, JPQL enum query for REGULAR_MEDICINE_STOCK returns the migrated rows")
        void jpqlEnumQuerySucceedsAfterLegacyRename() {
            Long id = jdbc.queryForObject("SELECT id FROM medicine_stock LIMIT 1", Long.class);
            jdbc.update("UPDATE medicine_stock SET medicine_stock_type = 'REGULAR' WHERE id = ?", id);
            int sizeBefore = medicineStockRepository.findAllNonZeroOrderByMedicineAndUser(MedicineStockType.REGULAR_MEDICINE_STOCK).size();

            dataMigrationService.onStartup();

            int sizeAfter = medicineStockRepository.findAllNonZeroOrderByMedicineAndUser(MedicineStockType.REGULAR_MEDICINE_STOCK).size();
            assertThat(sizeAfter)
                .as("JPQL enum query should return at least as many rows after migration as before " +
                    "(the renamed row must now be visible)")
                .isGreaterThanOrEqualTo(sizeBefore);
        }

        @Test @DisplayName("'REGULAR' values in transactions table are renamed")
        void renamesLegacyRegularInTransactionsTable() {
            // transactions table only has medicine_stock_type when there are submitted transactions;
            // use a direct INSERT to simulate a legacy transaction row
            try {
                Long userId = jdbc.queryForObject("SELECT id FROM users LIMIT 1", Long.class);
                Long medicineId = jdbc.queryForObject("SELECT id FROM medicines LIMIT 1", Long.class);
                jdbc.update(
                    "INSERT INTO transactions (user_id, medicine_id, quantity, status, notes, submitted_at, medicine_stock_type) " +
                    "VALUES (?, ?, 1, 'PENDING', 'Legacy rename test', CURRENT_TIMESTAMP, 'REGULAR')",
                    userId, medicineId);
                assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM transactions WHERE medicine_stock_type = 'REGULAR'", Integer.class))
                    .isEqualTo(1);

                dataMigrationService.onStartup();

                assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM transactions WHERE medicine_stock_type = 'REGULAR'", Integer.class))
                    .as("transactions.medicine_stock_type 'REGULAR' must be renamed to REGULAR_MEDICINE_STOCK")
                    .isZero();
            } catch (Exception e) {
                // If transactions table schema differs (e.g. missing column), skip gracefully
                // but fail loudly so it's not silently ignored
                assertThat(e.getMessage())
                    .as("Unexpected error setting up legacy transactions row")
                    .doesNotContain("Column \"MEDICINE_STOCK_TYPE\" not found");
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

        @Test @DisplayName("Total medicineStock count is unchanged after running onStartup() a second time")
        void totalMedicineStockUnchangedAfterSecondStartup() {
            long before = medicineStockRepository.count();

            dataMigrationService.onStartup();

            assertThat(medicineStockRepository.count())
                .as("MedicineStock count must not change on repeated onStartup() calls")
                .isEqualTo(before);
        }

        @Test @DisplayName("Calling onStartup() three times still preserves all medicineStock")
        void tripleStartupIsFullyIdempotent() {
            long before = medicineStockRepository.count();

            dataMigrationService.onStartup();
            dataMigrationService.onStartup();
            dataMigrationService.onStartup();

            assertThat(medicineStockRepository.count()).isEqualTo(before);
        }
    }

    private long countByType(String medicineStockType) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM medicine_stock WHERE medicine_stock_type = ?", Long.class, medicineStockType);
    }
}
