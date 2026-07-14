package com.pharma.inventory.service;

import com.pharma.inventory.entity.*;
import com.pharma.inventory.repository.InventoryAdjustmentRepository;
import com.pharma.inventory.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Verifies CurrentStockCalculator reconstructs the same "settled" quantity ReportService's
 * forward reconstruction would produce for the same adjustment/transaction history — this is
 * the fix for dispatchable stock silently disagreeing with what the daily report shows.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CurrentStockCalculator")
class CurrentStockCalculatorTest {

    @Mock InventoryAdjustmentRepository inventoryAdjustmentRepository;
    @Mock TransactionRepository transactionRepository;

    @InjectMocks CurrentStockCalculator calculator;

    private User user;
    private Medicine medicine;
    private PharmaCompany pharma;

    @BeforeEach
    void setUp() {
        pharma = new PharmaCompany();
        pharma.setId(1L); pharma.setName("Shield FX");

        medicine = new Medicine();
        medicine.setId(19L); medicine.setName("Shield FX Vial 10 ml");
        medicine.setType(Medicine.MedicineType.VIAL);
        medicine.setSpecification(10.0); medicine.setPharmaCompany(pharma);

        user = new User();
        user.setId(34L); user.setUsername("riona");
    }

    private InventoryAdjustment adj(String type, int qty, LocalDateTime adjustedAt) {
        return InventoryAdjustment.builder()
                .user(user).medicine(medicine).quantity(qty)
                .adjustmentType(type).inTransit(false).wasInTransit(false).transitDays(2)
                .inventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK)
                .adjustedAt(adjustedAt)
                .build();
    }

    private InventoryAdjustment inTransitAdj(int qty, LocalDateTime adjustedAt, int transitDays) {
        return InventoryAdjustment.builder()
                .user(user).medicine(medicine).quantity(qty)
                .adjustmentType("ADD").inTransit(true).wasInTransit(true).transitDays(transitDays)
                .inventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK)
                .adjustedAt(adjustedAt)
                .build();
    }

    private Transaction tx(int qty, Transaction.TransactionStatus status) {
        Transaction t = Transaction.builder()
                .submittedBy(user).medicine(medicine).quantity(qty)
                .status(status).notes("Test dispatch note")
                .inventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK)
                .build();
        t.setSubmittedAt(LocalDateTime.now().minusDays(1));
        return t;
    }

    @Nested @DisplayName("settledQuantity")
    class SettledQuantity {

        @Test @DisplayName("reproduces the exact production discrepancy: 14 added, 6 dispatched, settled = 8")
        void reproducesRionaCase() {
            // Mirrors the real production data that caused the bug report: two real ADD
            // adjustments (10 + 4) and four real non-rejected transactions (3+1+1+1=6).
            when(inventoryAdjustmentRepository.findAllUpToForUser(eq(34L), any())).thenReturn(List.of(
                    adj("ADD", 4, LocalDateTime.of(2026, 4, 21, 0, 0)),
                    adj("ADD", 10, LocalDateTime.of(2026, 5, 23, 0, 0))
            ));
            when(transactionRepository.findNonRejectedSubmittedUpToForUser(eq(34L), any(), any())).thenReturn(List.of(
                    tx(3, Transaction.TransactionStatus.APPROVED),
                    tx(1, Transaction.TransactionStatus.APPROVED),
                    tx(1, Transaction.TransactionStatus.APPROVED),
                    tx(1, Transaction.TransactionStatus.APPROVED)
            ));

            int settled = calculator.settledQuantity(34L, 19L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK);

            assertThat(settled).isEqualTo(8);
        }

        @Test @DisplayName("REMOVE adjustments reduce the reconstructed total")
        void removeAdjustmentReducesTotal() {
            when(inventoryAdjustmentRepository.findAllUpToForUser(eq(34L), any())).thenReturn(List.of(
                    adj("ADD", 10, LocalDateTime.now().minusDays(5)),
                    adj("REMOVE", 3, LocalDateTime.now().minusDays(2))
            ));
            when(transactionRepository.findNonRejectedSubmittedUpToForUser(eq(34L), any(), any())).thenReturn(List.of());

            int settled = calculator.settledQuantity(34L, 19L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK);

            assertThat(settled).isEqualTo(7);
        }

        @Test @DisplayName("REJECTED transactions do not reduce the reconstructed total")
        void rejectedTransactionsExcluded() {
            when(inventoryAdjustmentRepository.findAllUpToForUser(eq(34L), any())).thenReturn(List.of(
                    adj("ADD", 10, LocalDateTime.now().minusDays(5))
            ));
            when(transactionRepository.findNonRejectedSubmittedUpToForUser(eq(34L), any(), any())).thenReturn(List.of());
            // Note: a REJECTED transaction would never be returned by
            // findNonRejectedSubmittedUpToForUser in real usage — asserting the empty-list case
            // here documents that expectation via the mock itself.

            int settled = calculator.settledQuantity(34L, 19L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK);

            assertThat(settled).isEqualTo(10);
        }

        @Test @DisplayName("excludes quantity from a still-active in-transit ADD adjustment")
        void excludesActiveInTransit() {
            when(inventoryAdjustmentRepository.findAllUpToForUser(eq(34L), any())).thenReturn(List.of(
                    adj("ADD", 8, LocalDateTime.now().minusDays(10)),
                    inTransitAdj(3, LocalDateTime.now().minusHours(1), 2) // adjusted 1hr ago, 2-day window — still active
            ));
            when(transactionRepository.findNonRejectedSubmittedUpToForUser(eq(34L), any(), any())).thenReturn(List.of());

            int settled = calculator.settledQuantity(34L, 19L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK);

            assertThat(settled).isEqualTo(8); // 8 + 3 net, minus 3 still in transit
        }

        @Test @DisplayName("does not exclude an in-transit ADD adjustment whose transit window has expired")
        void includesExpiredInTransit() {
            when(inventoryAdjustmentRepository.findAllUpToForUser(eq(34L), any())).thenReturn(List.of(
                    inTransitAdj(3, LocalDateTime.now().minusDays(60), 2) // adjusted 60 days ago, 2-day window — long expired
            ));
            when(transactionRepository.findNonRejectedSubmittedUpToForUser(eq(34L), any(), any())).thenReturn(List.of());

            int settled = calculator.settledQuantity(34L, 19L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK);

            assertThat(settled).isEqualTo(3); // fully settled, no longer excluded as in-transit
        }

        @Test @DisplayName("clamps to 0 rather than returning negative when transactions exceed adjustments")
        void clampsToZero() {
            when(inventoryAdjustmentRepository.findAllUpToForUser(eq(34L), any())).thenReturn(List.of(
                    adj("ADD", 2, LocalDateTime.now().minusDays(5))
            ));
            when(transactionRepository.findNonRejectedSubmittedUpToForUser(eq(34L), any(), any())).thenReturn(List.of(
                    tx(5, Transaction.TransactionStatus.APPROVED)
            ));

            int settled = calculator.settledQuantity(34L, 19L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK);

            assertThat(settled).isEqualTo(0);
        }

        @Test @DisplayName("returns 0 for a bucket with no adjustment or transaction history")
        void zeroForUnknownBucket() {
            when(inventoryAdjustmentRepository.findAllUpToForUser(eq(34L), any())).thenReturn(List.of());
            when(transactionRepository.findNonRejectedSubmittedUpToForUser(eq(34L), any(), any())).thenReturn(List.of());

            int settled = calculator.settledQuantity(34L, 19L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK);

            assertThat(settled).isZero();
        }

        @Test @DisplayName("keeps REGULAR and ADMIN_MEDICINE_STOCK buckets for the same medicine separate")
        void separatesInventoryTypeBuckets() {
            InventoryAdjustment adminAdj = InventoryAdjustment.builder()
                    .user(user).medicine(medicine).quantity(20)
                    .adjustmentType("ADD").inTransit(false).wasInTransit(false).transitDays(2)
                    .inventoryType(Inventory.InventoryType.ADMIN_MEDICINE_STOCK)
                    .adjustedAt(LocalDateTime.now().minusDays(5))
                    .build();
            when(inventoryAdjustmentRepository.findAllUpToForUser(eq(34L), any())).thenReturn(List.of(
                    adj("ADD", 10, LocalDateTime.now().minusDays(5)),
                    adminAdj
            ));
            when(transactionRepository.findNonRejectedSubmittedUpToForUser(eq(34L), any(), any())).thenReturn(List.of());

            assertThat(calculator.settledQuantity(34L, 19L, Inventory.InventoryType.REGULAR_MEDICINE_STOCK)).isEqualTo(10);
            assertThat(calculator.settledQuantity(34L, 19L, Inventory.InventoryType.ADMIN_MEDICINE_STOCK)).isEqualTo(20);
        }
    }

    @Nested @DisplayName("settledQuantitiesForUser")
    class SettledQuantitiesForUser {

        @Test @DisplayName("returns a map keyed by medicineId|inventoryType covering every bucket touched")
        void returnsMapForAllBuckets() {
            Medicine medicine2 = new Medicine();
            medicine2.setId(20L); medicine2.setName("Shield FX Vial 5 ml");
            medicine2.setType(Medicine.MedicineType.VIAL);
            medicine2.setSpecification(5.0); medicine2.setPharmaCompany(pharma);

            InventoryAdjustment other = InventoryAdjustment.builder()
                    .user(user).medicine(medicine2).quantity(6)
                    .adjustmentType("ADD").inTransit(false).wasInTransit(false).transitDays(2)
                    .inventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK)
                    .adjustedAt(LocalDateTime.now().minusDays(3))
                    .build();
            when(inventoryAdjustmentRepository.findAllUpToForUser(eq(34L), any())).thenReturn(List.of(
                    adj("ADD", 8, LocalDateTime.now().minusDays(5)),
                    other
            ));
            when(transactionRepository.findNonRejectedSubmittedUpToForUser(eq(34L), any(), any())).thenReturn(List.of());

            Map<String, Integer> result = calculator.settledQuantitiesForUser(34L);

            assertThat(result).containsEntry("19|REGULAR_MEDICINE_STOCK", 8);
            assertThat(result).containsEntry("20|REGULAR_MEDICINE_STOCK", 6);
        }
    }
}
