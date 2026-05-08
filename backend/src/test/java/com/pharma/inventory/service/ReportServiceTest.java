package com.pharma.inventory.service;

import com.pharma.inventory.dto.ReportResponse;
import com.pharma.inventory.entity.*;
import com.pharma.inventory.repository.InventoryAdjustmentRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService")
class ReportServiceTest {

    @Mock InventoryRepository inventoryRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock InventoryAdjustmentRepository inventoryAdjustmentRepository;

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
        vial.setConcentrationMgPerMl(20.0);
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
        inv.setInventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
        return inv;
    }

    private Inventory makeAdminStockInv(Long id, User u, Medicine m, int qty, String note) {
        Inventory inv = makeInv(id, u, m, qty, note);
        inv.setInventoryType(Inventory.InventoryType.ADMIN_MEDICINE_STOCK);
        return inv;
    }

    private Transaction makeTx(Long id, User u, Medicine m, int qty, Transaction.TransactionStatus status, String notes) {
        return makeTx(id, u, m, qty, status, notes, LocalDateTime.now());
    }

    private Transaction makeTx(Long id, User u, Medicine m, int qty, Transaction.TransactionStatus status, String notes, LocalDateTime submittedAt) {
        Transaction tx = Transaction.builder()
                .id(id).submittedBy(u).medicine(m).quantity(qty)
                .status(status).notes(notes).submittedAt(submittedAt).build();
        if (status == Transaction.TransactionStatus.APPROVED) tx.setApprovedAt(LocalDateTime.now());
        return tx;
    }

    @Nested @DisplayName("inventoryByUser")
    class InventoryByUser {

        private void stubAdminStockEmpty() {
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser(Inventory.InventoryType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of());
        }

        @Test
        void reportContainsMedicineNameAndUserQuantity() {
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 50, "Restocked Ward 3")));
            stubAdminStockEmpty();

            ReportResponse r = reportService.inventoryByUser();

            assertThat(r.getReportType()).isEqualTo("INVENTORY_BY_USER");
            assertThat(r.getContent()).contains("Vial 10 ml | 20 mg/ml");
            assertThat(r.getContent()).contains("john.doe");
            assertThat(r.getContent()).contains("50");
        }

        @Test
        void reportUsesUsernameNotFullName() {
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 10, null)));
            stubAdminStockEmpty();

            ReportResponse r = reportService.inventoryByUser();

            assertThat(r.getContent()).contains("john.doe");
            assertThat(r.getContent()).doesNotContain("John Doe");
        }

        @Test
        void reportDoesNotShowNotes() {
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 10, "Restocked Ward 3")));
            stubAdminStockEmpty();

            ReportResponse r = reportService.inventoryByUser();

            assertThat(r.getContent()).doesNotContain("Restocked Ward 3");
            assertThat(r.getContent()).doesNotContain("Admin note");
            assertThat(r.getContent()).doesNotContain("User note");
        }

        @Test
        void tabletSectionHeaderHasNoSpecSuffix() {
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, tablet, 20, null)));
            stubAdminStockEmpty();

            ReportResponse r = reportService.inventoryByUser();

            assertThat(r.getContent()).contains("Tablet 25 mg (10 Tablets)");
            assertThat(r.getContent()).doesNotContain("Shield FX Tablet");
        }

        @Test
        void vialSectionHeaderShowsConcentration() {
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 10, null)));
            stubAdminStockEmpty();

            ReportResponse r = reportService.inventoryByUser();

            assertThat(r.getContent()).contains("Vial 10 ml | 20 mg/ml");
            assertThat(r.getContent()).doesNotContain("Shield FX Vial");
            assertThat(r.getContent()).doesNotContain("10.0 mg/ml");
        }

        @Test
        void reportShowsTotalAcrossUsers() {
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(
                            makeInv(1L, john, vial, 30, null),
                            makeInv(2L, jane, vial, 20, null)));
            stubAdminStockEmpty();

            ReportResponse r = reportService.inventoryByUser();

            assertThat(r.getContent()).contains("TOTAL: 50");
        }

        @Test
        void emptyInventoryProducesEmptyReport() {
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of());
            stubAdminStockEmpty();

            ReportResponse r = reportService.inventoryByUser();

            assertThat(r.getContent()).contains("CURRENT MEDICINE STOCK PER USER");
        }

        @Test
        void vialAppearsBeforeTabletInFixedOrder() {
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(
                            makeInv(1L, john, vial, 10, null),
                            makeInv(2L, john, tablet, 5, null)));
            stubAdminStockEmpty();

            ReportResponse r = reportService.inventoryByUser();

            int posVial   = r.getContent().indexOf("Vial 10 ml | 20 mg/ml");
            int posTablet = r.getContent().indexOf("Tablet 25 mg (10 Tablets)");
            assertThat(posVial).isLessThan(posTablet);
        }

        @Test
        void specsWithNoDataAreSkipped() {
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 10, null)));
            stubAdminStockEmpty();

            ReportResponse r = reportService.inventoryByUser();

            assertThat(r.getContent()).contains("Vial 10 ml | 20 mg/ml");
            assertThat(r.getContent()).doesNotContain("Vial 5 ml");
            assertThat(r.getContent()).doesNotContain("Tablet 25 mg");
        }

        @Test
        void reportContainsPharmaNameHeading() {
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 10, null)));
            stubAdminStockEmpty();

            ReportResponse r = reportService.inventoryByUser();

            assertThat(r.getContent()).contains("REGULAR MEDICINE STOCK");
            assertThat(r.getContent()).contains("Shield FX");
            assertThat(r.getContent()).contains("ADMIN MEDICINE STOCK");
        }

        @Test
        void reportContainsAdminInventorySectionAfterRegular() {
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 10, null)));
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser(Inventory.InventoryType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of(makeAdminStockInv(2L, john, vial, 5, null)));

            ReportResponse r = reportService.inventoryByUser();

            String content = r.getContent();
            assertThat(content).contains("REGULAR MEDICINE STOCK");
            assertThat(content).contains("ADMIN MEDICINE STOCK");
            int posRegular = content.indexOf("REGULAR MEDICINE STOCK");
            int posAdmin   = content.indexOf("ADMIN MEDICINE STOCK");
            assertThat(posRegular).isLessThan(posAdmin);
            // Admin section should have the admin stock quantity
            String adminBlock = content.substring(posAdmin);
            assertThat(adminBlock).contains("john.doe: 5");
        }

        @Test
        void adminInventorySectionSkipsEmptySpecs() {
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of());
            when(inventoryRepository.findAllNonZeroOrderByMedicineAndUser(Inventory.InventoryType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of(makeAdminStockInv(1L, john, vial, 7, null)));

            ReportResponse r = reportService.inventoryByUser();

            String content = r.getContent();
            int posAdmin = content.indexOf("ADMIN MEDICINE STOCK");
            String adminBlock = content.substring(posAdmin);
            // Only the vial spec should appear — no (none) for empty specs
            assertThat(adminBlock).doesNotContain("(none)");
            assertThat(adminBlock).doesNotContain("TOTAL: 0");
            assertThat(adminBlock).contains("john.doe: 7");
        }
    }

    @Nested @DisplayName("inventoryValuation")
    class InventoryValuation {

        @Test
        void reportCalculatesValuationCorrectly() {
            when(inventoryRepository.findAllNonZeroForValuation(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(
                            makeInv(1L, john, vial, 10, null),
                            makeInv(2L, jane, vial, 5, null)));

            ReportResponse r = reportService.inventoryValuation();

            assertThat(r.getReportType()).isEqualTo("INVENTORY_VALUATION");
            assertThat(r.getContent()).contains("Vial 10 ml");
            assertThat(r.getContent()).contains("Qty: 15");
            assertThat(r.getContent()).contains("60,000");
            assertThat(r.getContent()).contains("TOTAL VALUATION");
        }

        @Test
        void reportShowsGrandTotal() {
            when(inventoryRepository.findAllNonZeroForValuation(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(
                            makeInv(1L, john, vial, 5, null),
                            makeInv(2L, jane, tablet, 2, null)));

            ReportResponse r = reportService.inventoryValuation();

            // vial: 5 * 4000 = 20000, tablet: 2 * 4000 = 8000, total = 28000
            assertThat(r.getContent()).contains("28,000");
        }

        @Test
        void tabletHeaderHasNoSpecSuffix() {
            when(inventoryRepository.findAllNonZeroForValuation(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, tablet, 5, null)));

            ReportResponse r = reportService.inventoryValuation();

            assertThat(r.getContent()).contains("Tablet 25 mg (10 Tablets)");
        }

        @Test
        void pharmaNameHeadingAppearsInReport() {
            when(inventoryRepository.findAllNonZeroForValuation(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 5, null)));

            ReportResponse r = reportService.inventoryValuation();

            assertThat(r.getContent()).contains("Shield FX");
        }

        @Test
        void shortSpecNameUsedNotFullMedicineName() {
            when(inventoryRepository.findAllNonZeroForValuation(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 5, null)));

            ReportResponse r = reportService.inventoryValuation();

            assertThat(r.getContent()).contains("Vial 10 ml");
            assertThat(r.getContent()).doesNotContain("Shield FX Vial 10 ml\n");
        }

        @Test
        void emptyInventoryShowsZeroTotal() {
            when(inventoryRepository.findAllNonZeroForValuation(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of());

            ReportResponse r = reportService.inventoryValuation();

            assertThat(r.getContent()).contains("TOTAL VALUATION: Rs 0");
        }

        @Test
        void adminStockExcludedFromValuation() {
            // findAllNonZeroForValuation only returns REGULAR type (enforced by JPQL).
            // Verify the service totals only what the repository returns.
            when(inventoryRepository.findAllNonZeroForValuation(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 5, null)));

            ReportResponse r = reportService.inventoryValuation();

            assertThat(r.getContent()).contains("Qty: 5");
            assertThat(r.getContent()).contains("TOTAL VALUATION: Rs 20,000");
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

            assertThat(r.getReportType()).isEqualTo("SALES_REPORT");
            assertThat(r.getContent()).contains("SALES REPORT");
            assertThat(r.getContent()).contains("John Doe");
            assertThat(r.getContent()).contains("john.doe");
            assertThat(r.getContent()).contains("10 ml");
            assertThat(r.getContent()).contains("12,000");
        }

        @Test
        void reportShowsUsernameInTransactionLine() {
            Transaction tx = makeTx(1L, john, vial, 3,
                    Transaction.TransactionStatus.APPROVED, "sent to Vandana");
            when(transactionRepository.findApprovedBetween(any(Transaction.TransactionStatus.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of(tx));

            ReportResponse r = reportService.todaySales();

            assertThat(r.getContent()).contains("john.doe  3 x 10 ml  sent to Vandana");
        }

        @Test
        void tabletTransactionShowsMgSpecLabel() {
            Transaction tx = makeTx(1L, john, tablet, 2,
                    Transaction.TransactionStatus.APPROVED, "for clinic");
            when(transactionRepository.findApprovedBetween(any(Transaction.TransactionStatus.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of(tx));

            ReportResponse r = reportService.todaySales();

            assertThat(r.getContent()).contains("john.doe  2 x 25 mg  for clinic");
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

        @Test
        void pricePerUnitOverrideIsUsedInsteadOfDefaultMedicinePrice() {
            Transaction tx = makeTx(1L, john, vial, 1,
                    Transaction.TransactionStatus.APPROVED, "override price test");
            tx.setPricePerUnit(3500);
            when(transactionRepository.findApprovedBetween(any(Transaction.TransactionStatus.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of(tx));

            ReportResponse r = reportService.todaySales();

            assertThat(r.getContent()).contains("3,500");
            assertThat(r.getContent()).doesNotContain("4,000");
        }
    }

    @Nested @DisplayName("dailyReport")
    class DailyReport {

        private void stubEmpty() {
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of());
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());
        }

        @Test
        void reportTypeAndHeader() {
            stubEmpty();

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getReportType()).isEqualTo("DAILY_REPORT");
            assertThat(r.getContent()).contains("DAILY REPORT");
            assertThat(r.getContent()).contains("REGULAR MEDICINE STOCK");
            assertThat(r.getContent()).contains("ADMIN MEDICINE STOCK");
            assertThat(r.getContent()).contains("Shield FX");
            assertThat(r.getContent()).doesNotContain("INVENTORY COUNTS");
        }

        @Test
        void regularSectionAppearsBeforeAdminSection() {
            stubEmpty();

            ReportResponse r = reportService.dailyReport(null);

            String content = r.getContent();
            int posRegular = content.indexOf("REGULAR MEDICINE STOCK");
            int posAdmin   = content.indexOf("ADMIN MEDICINE STOCK");
            assertThat(posRegular).isLessThan(posAdmin);
        }

        @Test
        void timestampContainsIST() {
            stubEmpty();

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("IST");
            assertThat(r.getGeneratedAt()).endsWith("IST");
        }

        @Test
        void pharmaNameIsUsedAsHeadingFromInventoryData() {
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 10, null)));
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("Shield FX");
            assertThat(r.getContent()).doesNotContain("mg/ml");
            assertThat(r.getContent()).doesNotContain("Shield FX Vial");
        }

        @Test
        void vialAppearsBeforeTabletInFixedOrder() {
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(
                            makeInv(1L, john, vial, 10, null),
                            makeInv(2L, john, tablet, 5, null)));
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(null);

            int pos10ml = r.getContent().indexOf("Vial 10 ml");
            int pos25mg = r.getContent().indexOf("Tablet 25 mg");
            assertThat(pos10ml).isLessThan(pos25mg);
        }

        @Test
        void tenMlVialAppearsBeforeFiveMlVial() {
            stubEmpty();

            ReportResponse r = reportService.dailyReport(null);

            int pos10ml = r.getContent().indexOf("Vial 10 ml");
            int pos5ml  = r.getContent().indexOf("Vial 5 ml");
            assertThat(pos10ml).isLessThan(pos5ml);
        }

        @Test
        void showsUserQuantityUnderSpec() {
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 30, null)));
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("john.doe: 30");
            assertThat(r.getContent()).contains("TOTAL: 30");
        }

        @Test
        void showsNoneWhenSpecHasNoInventory() {
            stubEmpty();

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("(none)");
            assertThat(r.getContent()).contains("TOTAL: 0");
        }

        @Test
        void tabletTransactionShowsMgSuffix() {
            stubEmpty();
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(makeTx(1L, john, tablet, 2,
                            Transaction.TransactionStatus.APPROVED, "sent to Vandana")));

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("john.doe  2 x 25 mg  sent to Vandana");
        }

        @Test
        void vialTransactionShowsMlSuffix() {
            stubEmpty();
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(makeTx(1L, john, vial, 3,
                            Transaction.TransactionStatus.APPROVED, "Clinic B")));

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("john.doe  3 x 10 ml  Clinic B");
        }

        @Test
        void noTransactionsShowsMessage() {
            stubEmpty();

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("no transactions today");
        }

        @Test
        void multipleUsersForSameSpecShowSeparateLines() {
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(
                            makeInv(1L, john, vial, 30, null),
                            makeInv(2L, jane, vial, 20, null)));
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("john.doe: 30");
            assertThat(r.getContent()).contains("jane.smith: 20");
            assertThat(r.getContent()).contains("TOTAL: 50");
        }

        @Test
        void adminInventorySectionPresentBeforeTransactions() {
            stubEmpty();

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("ADMIN MEDICINE STOCK");
            int posAdmin = r.getContent().indexOf("ADMIN MEDICINE STOCK");
            int posTx    = r.getContent().indexOf("DAILY TRANSACTION SUMMARY");
            assertThat(posAdmin).isLessThan(posTx);
        }

        @Test
        void adminInventoryShowsCorrectQuantity() {
            // ADMIN_STOCK inventory belongs to non-admin users in the new model
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of());
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of(makeAdminStockInv(1L, john, vial, 15, null)));
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(null);

            String content = r.getContent();
            int adminSection = content.indexOf("ADMIN MEDICINE STOCK");
            int txSection    = content.indexOf("DAILY TRANSACTION SUMMARY");
            String adminBlock = content.substring(adminSection, txSection);
            assertThat(adminBlock).contains("john.doe: 15");
            assertThat(adminBlock).contains("TOTAL: 15");
        }

        @Test
        void adminInventorySkipsSpecsWithNoAdminStock() {
            // Feature 2: admin inventory section skips specs with no data (no (none)/TOTAL: 0)
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 10, null)));
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(null);

            String content = r.getContent();
            int adminSection = content.indexOf("ADMIN MEDICINE STOCK");
            int txSection    = content.indexOf("DAILY TRANSACTION SUMMARY");
            String adminBlock = content.substring(adminSection, txSection);
            // No specs should appear when admin stock is empty
            assertThat(adminBlock).doesNotContain("(none)");
            assertThat(adminBlock).doesNotContain("TOTAL: 0");
        }

        @Test
        void adminInventoryShowsOnlySpecsWithData() {
            // Only vial 10 ml has admin stock — other specs should not appear
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of());
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of(makeAdminStockInv(1L, john, vial, 8, null)));
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(null);

            String content = r.getContent();
            int adminSection = content.indexOf("ADMIN MEDICINE STOCK");
            int txSection    = content.indexOf("DAILY TRANSACTION SUMMARY");
            String adminBlock = content.substring(adminSection, txSection);
            assertThat(adminBlock).contains("Vial 10 ml");
            assertThat(adminBlock).contains("john.doe: 8");
            assertThat(adminBlock).doesNotContain("(none)");
            assertThat(adminBlock).doesNotContain("Vial 5 ml");
            assertThat(adminBlock).doesNotContain("Tablet");
        }

        @Test
        void adminInventoryDoesNotIncludeRegularInventory() {
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 30, null)));
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of(makeAdminStockInv(2L, john, vial, 5, null)));
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(null);

            String content = r.getContent();
            int adminSection = content.indexOf("ADMIN MEDICINE STOCK");
            int txSection    = content.indexOf("DAILY TRANSACTION SUMMARY");
            String adminBlock = content.substring(adminSection, txSection);
            assertThat(adminBlock).contains("john.doe: 5");
            assertThat(adminBlock).doesNotContain("30");
        }

        @Test
        void transactionLineHasUsernameAtFront() {
            stubEmpty();
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(makeTx(1L, john, vial, 3,
                            Transaction.TransactionStatus.APPROVED, "Clinic B")));

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("john.doe  3 x 10 ml  Clinic B");
        }

        @Test
        void transactionSectionNamedDailyTransactionSummary() {
            stubEmpty();

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("DAILY TRANSACTION SUMMARY");
        }

        @Test
        void dailyReportForSpecificDate() {
            stubEmpty();

            ReportResponse r = reportService.dailyReport(LocalDate.of(2026, 1, 15));

            assertThat(r.getContent()).contains("15 Jan 2026");
        }

        @Test
        void dailyReportNullDateDefaultsToToday() {
            stubEmpty();

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getReportType()).isEqualTo("DAILY_REPORT");
            assertThat(r.getContent()).contains("DAILY REPORT");
        }

        @Test
        void transactionSummaryFiltersOnDispatchDate() {
            // The repo must be queried with the report date's day boundaries so that
            // the SQL filters by submittedAt (dispatch date), not approvedAt.
            // A transaction dispatched on May 7 must not appear in the May 8 report.
            stubEmpty();
            LocalDate reportDate = LocalDate.of(2026, 5, 8);
            ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> endCaptor   = ArgumentCaptor.forClass(LocalDateTime.class);

            reportService.dailyReport(reportDate);

            verify(transactionRepository).findApprovedBetween(
                    eq(Transaction.TransactionStatus.APPROVED),
                    startCaptor.capture(),
                    endCaptor.capture());
            assertThat(startCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 5, 8, 0, 0));
            assertThat(endCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 5, 9, 0, 0));
        }

        @Test
        void transactionDispatchedOnPreviousDayIsExcludedFromReport() {
            // A transaction submitted (dispatched) on May 7 must NOT appear in the May 8 report.
            // The repo returns only transactions whose submittedAt falls within the day range.
            LocalDate reportDate = LocalDate.of(2026, 5, 8);
            LocalDateTime may7 = LocalDateTime.of(2026, 5, 7, 10, 0);
            Transaction may7tx = makeTx(1L, john, vial, 3, Transaction.TransactionStatus.APPROVED, "old", may7);

            // Simulate the repo correctly excluding may7tx (it's outside the May 8 range)
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of());
            when(inventoryRepository.findAllNonZeroByInventoryType(Inventory.InventoryType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(
                    eq(Transaction.TransactionStatus.APPROVED),
                    eq(LocalDateTime.of(2026, 5, 8, 0, 0)),
                    eq(LocalDateTime.of(2026, 5, 9, 0, 0))))
                    .thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(reportDate);

            assertThat(r.getContent()).contains("no transactions today");
            assertThat(r.getContent()).doesNotContain("john.doe  3 x 10 ml");
        }
    }
}
