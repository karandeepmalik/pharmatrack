package com.pharma.inventory.service;

import com.pharma.inventory.dto.ReportResponse;
import com.pharma.inventory.entity.*;
import com.pharma.inventory.repository.InventoryRepository;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService")
class ReportServiceTest {

    @Mock InventoryRepository inventoryRepository;
    @Mock TransactionRepository transactionRepository;

    @InjectMocks ReportService reportService;

    private PharmaCompany pharma;
    private Medicine vial;
    private Medicine tablet;
    private User john;
    private User jane;

    @BeforeEach
    void setUp() {
        pharma = new PharmaCompany();
        pharma.setId(1L); pharma.setName("Shield FX");

        vial = new Medicine();
        vial.setId(1L); vial.setName("Shield FX Vial 10 ml");
        vial.setType(Medicine.MedicineType.VIAL); vial.setSpecification(10.0);
        vial.setPrice(4000); vial.setPharmaCompany(pharma);

        tablet = new Medicine();
        tablet.setId(2L); tablet.setName("Shield FX Tablet 25 mg (10 Tablets)");
        tablet.setType(Medicine.MedicineType.TABLET); tablet.setSpecification(25.0);
        tablet.setPrice(4000); tablet.setPharmaCompany(pharma);

        john = User.builder().id(2L).username("john.doe").fullName("John Doe")
                .email("j@p.com").role(User.Role.USER).active(true).password("h").build();
        jane = User.builder().id(3L).username("jane.smith").fullName("Jane Smith")
                .email("js@p.com").role(User.Role.USER).active(true).password("h").build();
    }

    private Inventory makeInv(Long id, User u, Medicine m, int qty, String note) {
        Inventory inv = new Inventory();
        inv.setId(id); inv.setUser(u); inv.setMedicine(m);
        inv.setQuantity(qty); inv.setLastNote(note);
        inv.setLastUpdated(LocalDateTime.now());
        return inv;
    }

    private Transaction makeTx(Long id, User u, Medicine m, int qty, Transaction.TransactionStatus status, String notes) {
        Transaction tx = Transaction.builder()
                .id(id).submittedBy(u).medicine(m).quantity(qty)
                .status(status).notes(notes).build();
        if (status == Transaction.TransactionStatus.APPROVED) tx.setApprovedAt(LocalDateTime.now());
        return tx;
    }

    @Nested @DisplayName("inventoryByUser")
    class InventoryByUser {

        @Test
        void reportContainsMedicineNameAndUserQuantity() {
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser())
                    .thenReturn(List.of(makeInv(1L, john, vial, 50, "Restocked Ward 3")));
            when(transactionRepository.findAllByOrderBySubmittedAtDesc())
                    .thenReturn(List.of());

            ReportResponse r = reportService.inventoryByUser();

            assertThat(r.getReportType()).isEqualTo("INVENTORY_BY_USER");
            assertThat(r.getContent()).contains("Shield FX Vial 10 ml");
            assertThat(r.getContent()).contains("John Doe");
            assertThat(r.getContent()).contains("50 units");
            assertThat(r.getContent()).contains("Restocked Ward 3");
        }

        @Test
        void reportShowsTotalAcrossUsers() {
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser())
                    .thenReturn(List.of(
                            makeInv(1L, john, vial, 30, null),
                            makeInv(2L, jane, vial, 20, null)));
            when(transactionRepository.findAllByOrderBySubmittedAtDesc())
                    .thenReturn(List.of());

            ReportResponse r = reportService.inventoryByUser();

            assertThat(r.getContent()).contains("TOTAL: 50 units");
        }

        @Test
        void reportIncludesApprovedTransactionNotes() {
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser())
                    .thenReturn(List.of(makeInv(1L, john, vial, 40, null)));
            Transaction approved = makeTx(10L, john, vial, 5,
                    Transaction.TransactionStatus.APPROVED, "Dispatched to Clinic B");
            when(transactionRepository.findAllByOrderBySubmittedAtDesc())
                    .thenReturn(List.of(approved));

            ReportResponse r = reportService.inventoryByUser();

            assertThat(r.getContent()).contains("Dispatched to Clinic B");
        }

        @Test
        void emptyInventoryProducesEmptyReport() {
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser())
                    .thenReturn(List.of());
            when(transactionRepository.findAllByOrderBySubmittedAtDesc())
                    .thenReturn(List.of());

            ReportResponse r = reportService.inventoryByUser();

            assertThat(r.getContent()).contains("CURRENT INVENTORY LEVEL BY USER");
        }
    }

    @Nested @DisplayName("inventoryValuation")
    class InventoryValuation {

        @Test
        void reportCalculatesValuationCorrectly() {
            when(inventoryRepository.findAllNonZeroForValuation())
                    .thenReturn(List.of(
                            makeInv(1L, john, vial, 10, null),
                            makeInv(2L, jane, vial, 5, null)));

            ReportResponse r = reportService.inventoryValuation();

            assertThat(r.getReportType()).isEqualTo("INVENTORY_VALUATION");
            assertThat(r.getContent()).contains("Shield FX Vial 10 ml");
            assertThat(r.getContent()).contains("Qty: 15");
            assertThat(r.getContent()).contains("60,000");
            assertThat(r.getContent()).contains("TOTAL VALUATION");
        }

        @Test
        void reportShowsGrandTotal() {
            when(inventoryRepository.findAllNonZeroForValuation())
                    .thenReturn(List.of(
                            makeInv(1L, john, vial, 5, null),
                            makeInv(2L, jane, tablet, 2, null)));

            ReportResponse r = reportService.inventoryValuation();

            // vial: 5 * 4000 = 20000, tablet: 2 * 4000 = 8000, total = 28000
            assertThat(r.getContent()).contains("28,000");
        }

        @Test
        void emptyInventoryShowsZeroTotal() {
            when(inventoryRepository.findAllNonZeroForValuation())
                    .thenReturn(List.of());

            ReportResponse r = reportService.inventoryValuation();

            assertThat(r.getContent()).contains("TOTAL VALUATION: Rs 0");
        }
    }

    @Nested @DisplayName("todaySales")
    class TodaySales {

        @Test
        void reportShowsSalesGroupedByUser() {
            Transaction tx = makeTx(1L, john, vial, 3,
                    Transaction.TransactionStatus.APPROVED, "Clinic B");
            when(transactionRepository.findApprovedBetween(any(Transaction.TransactionStatus.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of(tx));

            ReportResponse r = reportService.todaySales();

            assertThat(r.getReportType()).isEqualTo("TODAY_SALES");
            assertThat(r.getContent()).contains("John Doe");
            assertThat(r.getContent()).contains("Shield FX Vial 10 ml");
            assertThat(r.getContent()).contains("Qty: 3");
            assertThat(r.getContent()).contains("12,000");
        }

        @Test
        void reportShowsGrandTotal() {
            Transaction tx1 = makeTx(1L, john, vial, 2,
                    Transaction.TransactionStatus.APPROVED, "note1");
            Transaction tx2 = makeTx(2L, jane, tablet, 1,
                    Transaction.TransactionStatus.APPROVED, "note2");
            when(transactionRepository.findApprovedBetween(any(Transaction.TransactionStatus.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of(tx1, tx2));

            ReportResponse r = reportService.todaySales();

            // vial: 2 * 4000 = 8000, tablet: 1 * 4000 = 4000, total = 12000
            assertThat(r.getContent()).contains("TOTAL: Rs 12,000");
        }

        @Test
        void noSalesTodayShowsMessage() {
            when(transactionRepository.findApprovedBetween(any(Transaction.TransactionStatus.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of());

            ReportResponse r = reportService.todaySales();

            assertThat(r.getContent()).contains("No sales recorded today");
        }

        @Test
        void reportIncludesTransactionNotes() {
            Transaction tx = makeTx(1L, john, vial, 1,
                    Transaction.TransactionStatus.APPROVED, "Special dispatch for FIP treatment");
            when(transactionRepository.findApprovedBetween(any(Transaction.TransactionStatus.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of(tx));

            ReportResponse r = reportService.todaySales();

            assertThat(r.getContent()).contains("Special dispatch for FIP treatment");
        }
    }
}
