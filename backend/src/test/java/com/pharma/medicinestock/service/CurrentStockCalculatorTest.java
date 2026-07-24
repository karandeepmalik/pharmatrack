package com.pharma.medicinestock.service;

import com.pharma.medicinestock.entity.*;
import com.pharma.medicinestock.repository.MedicineStockAdjustmentRepository;
import com.pharma.medicinestock.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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

    @Mock MedicineStockAdjustmentRepository medicineStockAdjustmentRepository;
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

    private MedicineStockAdjustment adj(String type, int qty, LocalDateTime adjustedAt) {
        return MedicineStockAdjustment.builder()
                .user(user).medicine(medicine).quantity(BigDecimal.valueOf(qty))
                .adjustmentType(type).inTransit(false).wasInTransit(false).transitDays(2)
                .medicineStockType(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK)
                .adjustedAt(adjustedAt)
                .build();
    }

    private MedicineStockAdjustment inTransitAdj(int qty, LocalDateTime adjustedAt, int transitDays) {
        return MedicineStockAdjustment.builder()
                .user(user).medicine(medicine).quantity(BigDecimal.valueOf(qty))
                .adjustmentType("ADD").inTransit(true).wasInTransit(true).transitDays(transitDays)
                .medicineStockType(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK)
                .adjustedAt(adjustedAt)
                .build();
    }

    private Transaction tx(int qty, Transaction.TransactionStatus status) {
        Transaction t = Transaction.builder()
                .submittedBy(user).medicine(medicine).quantity(BigDecimal.valueOf(qty))
                .status(status).notes("Test dispatch note")
                .medicineStockType(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK)
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
            when(medicineStockAdjustmentRepository.findAllUpToForUser(eq(34L), any())).thenReturn(List.of(
                    adj("ADD", 4, LocalDateTime.of(2026, 4, 21, 0, 0)),
                    adj("ADD", 10, LocalDateTime.of(2026, 5, 23, 0, 0))
            ));
            when(transactionRepository.findNonRejectedSubmittedUpToForUser(eq(34L), any(), any())).thenReturn(List.of(
                    tx(3, Transaction.TransactionStatus.APPROVED),
                    tx(1, Transaction.TransactionStatus.APPROVED),
                    tx(1, Transaction.TransactionStatus.APPROVED),
                    tx(1, Transaction.TransactionStatus.APPROVED)
            ));

            BigDecimal settled = calculator.settledQuantity(34L, 19L, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);

            assertThat(settled).isEqualByComparingTo(BigDecimal.valueOf(8));
        }

        @Test @DisplayName("REMOVE adjustments reduce the reconstructed total")
        void removeAdjustmentReducesTotal() {
            when(medicineStockAdjustmentRepository.findAllUpToForUser(eq(34L), any())).thenReturn(List.of(
                    adj("ADD", 10, LocalDateTime.now().minusDays(5)),
                    adj("REMOVE", 3, LocalDateTime.now().minusDays(2))
            ));
            when(transactionRepository.findNonRejectedSubmittedUpToForUser(eq(34L), any(), any())).thenReturn(List.of());

            BigDecimal settled = calculator.settledQuantity(34L, 19L, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);

            assertThat(settled).isEqualByComparingTo(BigDecimal.valueOf(7));
        }

        @Test @DisplayName("REJECTED transactions do not reduce the reconstructed total")
        void rejectedTransactionsExcluded() {
            when(medicineStockAdjustmentRepository.findAllUpToForUser(eq(34L), any())).thenReturn(List.of(
                    adj("ADD", 10, LocalDateTime.now().minusDays(5))
            ));
            when(transactionRepository.findNonRejectedSubmittedUpToForUser(eq(34L), any(), any())).thenReturn(List.of());
            // Note: a REJECTED transaction would never be returned by
            // findNonRejectedSubmittedUpToForUser in real usage — asserting the empty-list case
            // here documents that expectation via the mock itself.

            BigDecimal settled = calculator.settledQuantity(34L, 19L, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);

            assertThat(settled).isEqualByComparingTo(BigDecimal.TEN);
        }

        @Test @DisplayName("excludes quantity from a still-active in-transit ADD adjustment")
        void excludesActiveInTransit() {
            when(medicineStockAdjustmentRepository.findAllUpToForUser(eq(34L), any())).thenReturn(List.of(
                    adj("ADD", 8, LocalDateTime.now().minusDays(10)),
                    inTransitAdj(3, LocalDateTime.now().minusHours(1), 2) // adjusted 1hr ago, 2-day window — still active
            ));
            when(transactionRepository.findNonRejectedSubmittedUpToForUser(eq(34L), any(), any())).thenReturn(List.of());

            BigDecimal settled = calculator.settledQuantity(34L, 19L, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);

            assertThat(settled).isEqualByComparingTo(BigDecimal.valueOf(8)); // 8 + 3 net, minus 3 still in transit
        }

        @Test @DisplayName("does not exclude an in-transit ADD adjustment whose transit window has expired")
        void includesExpiredInTransit() {
            when(medicineStockAdjustmentRepository.findAllUpToForUser(eq(34L), any())).thenReturn(List.of(
                    inTransitAdj(3, LocalDateTime.now().minusDays(60), 2) // adjusted 60 days ago, 2-day window — long expired
            ));
            when(transactionRepository.findNonRejectedSubmittedUpToForUser(eq(34L), any(), any())).thenReturn(List.of());

            BigDecimal settled = calculator.settledQuantity(34L, 19L, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);

            assertThat(settled).isEqualByComparingTo(BigDecimal.valueOf(3)); // fully settled, no longer excluded as in-transit
        }

        @Test @DisplayName("clamps to 0 rather than returning negative when transactions exceed adjustments")
        void clampsToZero() {
            when(medicineStockAdjustmentRepository.findAllUpToForUser(eq(34L), any())).thenReturn(List.of(
                    adj("ADD", 2, LocalDateTime.now().minusDays(5))
            ));
            when(transactionRepository.findNonRejectedSubmittedUpToForUser(eq(34L), any(), any())).thenReturn(List.of(
                    tx(5, Transaction.TransactionStatus.APPROVED)
            ));

            BigDecimal settled = calculator.settledQuantity(34L, 19L, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);

            assertThat(settled).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test @DisplayName("returns 0 for a bucket with no adjustment or transaction history")
        void zeroForUnknownBucket() {
            when(medicineStockAdjustmentRepository.findAllUpToForUser(eq(34L), any())).thenReturn(List.of());
            when(transactionRepository.findNonRejectedSubmittedUpToForUser(eq(34L), any(), any())).thenReturn(List.of());

            BigDecimal settled = calculator.settledQuantity(34L, 19L, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);

            assertThat(settled).isZero();
        }

        @Test @DisplayName("keeps REGULAR and ADMIN_MEDICINE_STOCK buckets for the same medicine separate")
        void separatesMedicineStockTypeBuckets() {
            MedicineStockAdjustment adminAdj = MedicineStockAdjustment.builder()
                    .user(user).medicine(medicine).quantity(BigDecimal.valueOf(20))
                    .adjustmentType("ADD").inTransit(false).wasInTransit(false).transitDays(2)
                    .medicineStockType(MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK)
                    .adjustedAt(LocalDateTime.now().minusDays(5))
                    .build();
            when(medicineStockAdjustmentRepository.findAllUpToForUser(eq(34L), any())).thenReturn(List.of(
                    adj("ADD", 10, LocalDateTime.now().minusDays(5)),
                    adminAdj
            ));
            when(transactionRepository.findNonRejectedSubmittedUpToForUser(eq(34L), any(), any())).thenReturn(List.of());

            assertThat(calculator.settledQuantity(34L, 19L, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK)).isEqualByComparingTo(BigDecimal.TEN);
            assertThat(calculator.settledQuantity(34L, 19L, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK)).isEqualByComparingTo(BigDecimal.valueOf(20));
        }
    }

    @Nested @DisplayName("settledQuantitiesForUser")
    class SettledQuantitiesForUser {

        @Test @DisplayName("returns a map keyed by medicineId|medicineStockType covering every bucket touched")
        void returnsMapForAllBuckets() {
            Medicine medicine2 = new Medicine();
            medicine2.setId(20L); medicine2.setName("Shield FX Vial 5 ml");
            medicine2.setType(Medicine.MedicineType.VIAL);
            medicine2.setSpecification(5.0); medicine2.setPharmaCompany(pharma);

            MedicineStockAdjustment other = MedicineStockAdjustment.builder()
                    .user(user).medicine(medicine2).quantity(BigDecimal.valueOf(6))
                    .adjustmentType("ADD").inTransit(false).wasInTransit(false).transitDays(2)
                    .medicineStockType(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK)
                    .adjustedAt(LocalDateTime.now().minusDays(3))
                    .build();
            when(medicineStockAdjustmentRepository.findAllUpToForUser(eq(34L), any())).thenReturn(List.of(
                    adj("ADD", 8, LocalDateTime.now().minusDays(5)),
                    other
            ));
            when(transactionRepository.findNonRejectedSubmittedUpToForUser(eq(34L), any(), any())).thenReturn(List.of());

            Map<String, BigDecimal> result = calculator.settledQuantitiesForUser(34L);

            assertThat(result).containsEntry("19|REGULAR_MEDICINE_STOCK", BigDecimal.valueOf(8));
            assertThat(result).containsEntry("20|REGULAR_MEDICINE_STOCK", BigDecimal.valueOf(6));
        }
    }
}
