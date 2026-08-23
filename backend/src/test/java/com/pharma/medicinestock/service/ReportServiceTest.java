package com.pharma.medicinestock.service;

import com.pharma.medicinestock.dto.ReportResponse;
import com.pharma.medicinestock.dto.SalesGraphResponse;
import com.pharma.medicinestock.entity.*;
import com.pharma.medicinestock.repository.MedicineStockAdjustmentRepository;
import com.pharma.medicinestock.repository.MedicineStockRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService")
class ReportServiceTest {

    @Mock MedicineStockRepository medicineStockRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock MedicineStockAdjustmentRepository medicineStockAdjustmentRepository;

    @InjectMocks ReportService reportService;

    private PharmaCompany pharma;
    private Medicine vial;
    private Medicine vial5;
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

        vial5 = new Medicine();
        vial5.setId(3L); vial5.setName("Shield FX Vial 5 ml");
        vial5.setType(Medicine.MedicineType.VIAL); vial5.setSpecification(5.0);
        vial5.setConcentrationMgPerMl(20.0);
        vial5.setPrice(3000); vial5.setPharmaCompany(pharma);

        tablet = new Medicine();
        tablet.setId(2L); tablet.setName("Shield FX Tablet 25 mg (10 Tablets)");
        tablet.setType(Medicine.MedicineType.TABLET); tablet.setSpecification(25.0);
        tablet.setPrice(4000); tablet.setPharmaCompany(pharma);

        john = User.builder().id(2L).username("john.doe").fullName("John Doe")
                .email("j@p.com").role(User.Role.USER).active(true).password("h").build();
        jane = User.builder().id(3L).username("jane.smith").fullName("Jane Smith")
                .email("js@p.com").role(User.Role.USER).active(true).password("h").build();
    }

    private MedicineStock makeInv(Long id, User u, Medicine m, int qty, String note) {
        MedicineStock inv = new MedicineStock();
        inv.setId(id); inv.setUser(u); inv.setMedicine(m);
        inv.setQuantity(BigDecimal.valueOf(qty)); inv.setLastNote(note);
        inv.setLastUpdated(LocalDateTime.now());
        inv.setMedicineStockType(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
        return inv;
    }

    private MedicineStock makeAdminStockInv(Long id, User u, Medicine m, int qty, String note) {
        MedicineStock inv = makeInv(id, u, m, qty, note);
        inv.setMedicineStockType(MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK);
        return inv;
    }

    private Transaction makeTx(Long id, User u, Medicine m, int qty, Transaction.TransactionStatus status, String notes) {
        return makeTx(id, u, m, qty, status, notes, LocalDateTime.now());
    }

    private Transaction makeTx(Long id, User u, Medicine m, int qty, Transaction.TransactionStatus status, String notes, LocalDateTime submittedAt) {
        Transaction tx = Transaction.builder()
                .id(id).submittedBy(u).medicine(m).quantity(BigDecimal.valueOf(qty))
                .status(status).notes(notes).submittedAt(submittedAt).build();
        if (status == Transaction.TransactionStatus.APPROVED) tx.setApprovedAt(LocalDateTime.now());
        return tx;
    }

    @Nested @DisplayName("medicineStockByUser")
    class MedicineStockByUser {

        private void stubAdminStockEmpty() {
            when(medicineStockRepository.findAllNonZeroOrderByMedicineAndUser(MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of());
        }

        @Test
        void reportContainsMedicineNameAndUserQuantity() {
            when(medicineStockRepository.findAllNonZeroRegularOrderByMedicineAndUser(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 50, "Restocked Ward 3")));
            stubAdminStockEmpty();

            ReportResponse r = reportService.medicineStockByUser();

            assertThat(r.getReportType()).isEqualTo("MEDICINE_STOCK_BY_USER");
            assertThat(r.getContent()).contains("Vial 10 ml | 20 mg/ml");
            assertThat(r.getContent()).contains("john.doe");
            assertThat(r.getContent()).contains("50");
        }

        @Test
        void reportUsesUsernameNotFullName() {
            when(medicineStockRepository.findAllNonZeroRegularOrderByMedicineAndUser(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 10, null)));
            stubAdminStockEmpty();

            ReportResponse r = reportService.medicineStockByUser();

            assertThat(r.getContent()).contains("john.doe");
            assertThat(r.getContent()).doesNotContain("John Doe");
        }

        @Test
        void reportDoesNotShowNotes() {
            when(medicineStockRepository.findAllNonZeroRegularOrderByMedicineAndUser(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 10, "Restocked Ward 3")));
            stubAdminStockEmpty();

            ReportResponse r = reportService.medicineStockByUser();

            assertThat(r.getContent()).doesNotContain("Restocked Ward 3");
            assertThat(r.getContent()).doesNotContain("Admin note");
            assertThat(r.getContent()).doesNotContain("User note");
        }

        @Test
        void tabletSectionHeaderHasNoSpecSuffix() {
            when(medicineStockRepository.findAllNonZeroRegularOrderByMedicineAndUser(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, tablet, 20, null)));
            stubAdminStockEmpty();

            ReportResponse r = reportService.medicineStockByUser();

            assertThat(r.getContent()).contains("Tablet 25 mg (10 Tablets)");
            assertThat(r.getContent()).doesNotContain("Shield FX Tablet");
        }

        @Test
        void vialSectionHeaderShowsConcentration() {
            when(medicineStockRepository.findAllNonZeroRegularOrderByMedicineAndUser(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 10, null)));
            stubAdminStockEmpty();

            ReportResponse r = reportService.medicineStockByUser();

            assertThat(r.getContent()).contains("Vial 10 ml | 20 mg/ml");
            assertThat(r.getContent()).doesNotContain("Shield FX Vial");
            assertThat(r.getContent()).doesNotContain("10.0 mg/ml");
        }

        @Test
        void reportShowsTotalAcrossUsers() {
            when(medicineStockRepository.findAllNonZeroRegularOrderByMedicineAndUser(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(
                            makeInv(1L, john, vial, 30, null),
                            makeInv(2L, jane, vial, 20, null)));
            stubAdminStockEmpty();

            ReportResponse r = reportService.medicineStockByUser();

            assertThat(r.getContent()).contains("TOTAL: 50");
        }

        @Test
        void emptyMedicineStockProducesEmptyReport() {
            when(medicineStockRepository.findAllNonZeroRegularOrderByMedicineAndUser(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of());
            stubAdminStockEmpty();

            ReportResponse r = reportService.medicineStockByUser();

            assertThat(r.getContent()).contains("CURRENT MEDICINE STOCK PER USER");
        }

        @Test
        void vialAppearsBeforeTabletInFixedOrder() {
            when(medicineStockRepository.findAllNonZeroRegularOrderByMedicineAndUser(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(
                            makeInv(1L, john, vial, 10, null),
                            makeInv(2L, john, tablet, 5, null)));
            stubAdminStockEmpty();

            ReportResponse r = reportService.medicineStockByUser();

            int posVial   = r.getContent().indexOf("Vial 10 ml | 20 mg/ml");
            int posTablet = r.getContent().indexOf("Tablet 25 mg (10 Tablets)");
            assertThat(posVial).isLessThan(posTablet);
        }

        @Test
        void specsWithNoDataAreSkipped() {
            when(medicineStockRepository.findAllNonZeroRegularOrderByMedicineAndUser(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 10, null)));
            stubAdminStockEmpty();

            ReportResponse r = reportService.medicineStockByUser();

            assertThat(r.getContent()).contains("Vial 10 ml | 20 mg/ml");
            assertThat(r.getContent()).doesNotContain("Vial 5 ml");
            assertThat(r.getContent()).doesNotContain("Tablet 25 mg");
        }

        @Test
        void reportContainsPharmaNameHeading() {
            when(medicineStockRepository.findAllNonZeroRegularOrderByMedicineAndUser(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 10, null)));
            stubAdminStockEmpty();

            ReportResponse r = reportService.medicineStockByUser();

            assertThat(r.getContent()).contains("REGULAR MEDICINE STOCK");
            assertThat(r.getContent()).contains("Shield FX");
            assertThat(r.getContent()).contains("ADMIN MEDICINE STOCK");
        }

        @Test
        void medicineWithSpecOutsideFixedOrderStillAppears() {
            // A spec DAILY_SPEC_ORDER doesn't know about (e.g. a new manufacturer's catalogue
            // entry) must still be rendered under its own heading, not silently dropped.
            Medicine capsule = new Medicine();
            capsule.setId(9L); capsule.setName("Shield FX Capsule 500 mg");
            capsule.setType(Medicine.MedicineType.CAPSULE); capsule.setSpecification(500.0);
            capsule.setPrice(1000); capsule.setPharmaCompany(pharma);

            when(medicineStockRepository.findAllNonZeroRegularOrderByMedicineAndUser(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, capsule, 15, null)));
            stubAdminStockEmpty();

            ReportResponse r = reportService.medicineStockByUser();

            assertThat(r.getContent()).contains("Capsule 500 mg");
            assertThat(r.getContent()).contains("john.doe: 15");
            assertThat(r.getContent()).contains("TOTAL: 15");
        }

        @Test
        void reportContainsAdminMedicineStockSectionAfterRegular() {
            when(medicineStockRepository.findAllNonZeroRegularOrderByMedicineAndUser(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 10, null)));
            when(medicineStockRepository.findAllNonZeroOrderByMedicineAndUser(MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of(makeAdminStockInv(2L, john, vial, 5, null)));

            ReportResponse r = reportService.medicineStockByUser();

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
        void adminMedicineStockSectionSkipsEmptySpecs() {
            when(medicineStockRepository.findAllNonZeroRegularOrderByMedicineAndUser(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of());
            when(medicineStockRepository.findAllNonZeroOrderByMedicineAndUser(MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of(makeAdminStockInv(1L, john, vial, 7, null)));

            ReportResponse r = reportService.medicineStockByUser();

            String content = r.getContent();
            int posAdmin = content.indexOf("ADMIN MEDICINE STOCK");
            String adminBlock = content.substring(posAdmin);
            // Only the vial spec should appear — no (none) for empty specs
            assertThat(adminBlock).doesNotContain("(none)");
            assertThat(adminBlock).doesNotContain("TOTAL: 0");
            assertThat(adminBlock).contains("john.doe: 7");
        }

        @Test
        void twoBlankLinesSeparateOneManufacturersSpecsFromTheNext() {
            PharmaCompany otherPharma = new PharmaCompany();
            otherPharma.setId(2L); otherPharma.setName("Acme Labs");
            Medicine otherVial = new Medicine();
            otherVial.setId(20L); otherVial.setName("Acme Labs Vial 10 ml");
            otherVial.setType(Medicine.MedicineType.VIAL); otherVial.setSpecification(10.0);
            otherVial.setConcentrationMgPerMl(20.0);
            otherVial.setPrice(4000); otherVial.setPharmaCompany(otherPharma);

            when(medicineStockRepository.findAllNonZeroRegularOrderByMedicineAndUser(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(
                            makeInv(1L, john, vial, 10, null),
                            makeInv(2L, john, otherVial, 5, null)));
            stubAdminStockEmpty();

            ReportResponse r = reportService.medicineStockByUser();

            int endOfShieldFxBlock = r.getContent().indexOf("TOTAL: 10") + "TOTAL: 10".length();
            int startOfAcmeHeading = r.getContent().indexOf("Acme Labs");
            String gap = r.getContent().substring(endOfShieldFxBlock, startOfAcmeHeading);
            assertThat(gap).isEqualTo("\n\n\n");
        }
    }

    @Nested @DisplayName("medicineStockValuation")
    class MedicineStockValuation {

        private void stubInTransitEmpty() {
            when(medicineStockAdjustmentRepository.findAllActiveInTransit()).thenReturn(List.of());
        }

        @Test
        void reportCalculatesValuationCorrectly() {
            when(medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(
                            makeInv(1L, john, vial, 10, null),
                            makeInv(2L, jane, vial, 5, null)));
            stubInTransitEmpty();

            ReportResponse r = reportService.medicineStockValuation();

            assertThat(r.getReportType()).isEqualTo("MEDICINE_STOCK_VALUATION");
            assertThat(r.getContent()).contains("Vial 10 ml");
            assertThat(r.getContent()).contains("john.doe: 10");
            assertThat(r.getContent()).contains("jane.smith: 5");
            assertThat(r.getContent()).contains("Valuation: 15 units x Rs 4,000 = Rs 60,000");
            assertThat(r.getContent()).contains("TOTAL VALUATION");
        }

        @Test
        void reportHeaderIsMedicineStockValuation() {
            when(medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of());
            stubInTransitEmpty();

            ReportResponse r = reportService.medicineStockValuation();

            assertThat(r.getContent()).contains("MEDICINE STOCK VALUATION");
            assertThat(r.getContent()).doesNotContain("CURRENT MEDICINE STOCK VALUATION");
        }

        @Test
        void reportShowsGrandTotal() {
            when(medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(
                            makeInv(1L, john, vial, 5, null),
                            makeInv(2L, jane, tablet, 2, null)));
            stubInTransitEmpty();

            ReportResponse r = reportService.medicineStockValuation();

            // vial: 5 * 4000 = 20000, tablet: 2 * 4000 = 8000, total = 28000
            assertThat(r.getContent()).contains("28,000");
        }

        @Test
        void tabletHeaderHasNoSpecSuffix() {
            when(medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, tablet, 5, null)));
            stubInTransitEmpty();

            ReportResponse r = reportService.medicineStockValuation();

            assertThat(r.getContent()).contains("Tablet 25 mg (10 Tablets)");
        }

        @Test
        void pharmaNameHeadingAppearsInReport() {
            when(medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 5, null)));
            stubInTransitEmpty();

            ReportResponse r = reportService.medicineStockValuation();

            assertThat(r.getContent()).contains("Shield FX");
        }

        @Test
        void shortSpecNameUsedNotFullMedicineName() {
            when(medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 5, null)));
            stubInTransitEmpty();

            ReportResponse r = reportService.medicineStockValuation();

            assertThat(r.getContent()).contains("Vial 10 ml");
            assertThat(r.getContent()).doesNotContain("Shield FX Vial 10 ml\n");
        }

        @Test
        void emptyMedicineStockShowsZeroTotal() {
            when(medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of());
            stubInTransitEmpty();

            ReportResponse r = reportService.medicineStockValuation();

            assertThat(r.getContent()).contains("TOTAL VALUATION: Rs 0");
        }

        @Test
        void medicineWithSpecOutsideFixedOrderStillAppearsInValuation() {
            Medicine capsule = new Medicine();
            capsule.setId(9L); capsule.setName("Shield FX Capsule 500 mg");
            capsule.setType(Medicine.MedicineType.CAPSULE); capsule.setSpecification(500.0);
            capsule.setPrice(1000); capsule.setPharmaCompany(pharma);

            when(medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, capsule, 5, null)));
            stubInTransitEmpty();

            ReportResponse r = reportService.medicineStockValuation();

            assertThat(r.getContent()).contains("Capsule 500 mg");
            assertThat(r.getContent()).contains("Valuation: 5 units x Rs 1,000 = Rs 5,000");
        }

        @Test
        void twoBlankLinesSeparateOneManufacturersSpecsFromTheNext() {
            PharmaCompany otherPharma = new PharmaCompany();
            otherPharma.setId(2L); otherPharma.setName("Acme Labs");
            Medicine otherVial = new Medicine();
            otherVial.setId(20L); otherVial.setName("Acme Labs Vial 10 ml");
            otherVial.setType(Medicine.MedicineType.VIAL); otherVial.setSpecification(10.0);
            otherVial.setConcentrationMgPerMl(20.0);
            otherVial.setPrice(4000); otherVial.setPharmaCompany(otherPharma);

            when(medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(
                            makeInv(1L, john, vial, 10, null),
                            makeInv(2L, john, otherVial, 5, null)));
            stubInTransitEmpty();

            ReportResponse r = reportService.medicineStockValuation();

            int endOfShieldFxBlock = r.getContent().indexOf("Rs 40,000") + "Rs 40,000".length();
            int startOfAcmeHeading = r.getContent().indexOf("Acme Labs");
            String gap = r.getContent().substring(endOfShieldFxBlock, startOfAcmeHeading);
            assertThat(gap).isEqualTo("\n\n\n");
        }

        @Test
        void adminStockExcludedFromValuation() {
            // findAllNonZeroForValuation only returns REGULAR type (enforced by JPQL).
            // Verify the service totals only what the repository returns.
            when(medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 5, null)));
            stubInTransitEmpty();

            ReportResponse r = reportService.medicineStockValuation();

            assertThat(r.getContent()).contains("john.doe: 5");
            assertThat(r.getContent()).contains("Valuation: 5 units x Rs 4,000 = Rs 20,000");
            assertThat(r.getContent()).contains("TOTAL VALUATION: Rs 20,000");
        }

        @Test
        void perUserBreakdownShownUnderEachSpec() {
            when(medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(
                            makeInv(1L, john, vial, 10, null),
                            makeInv(2L, jane, vial, 6, null)));
            stubInTransitEmpty();

            ReportResponse r = reportService.medicineStockValuation();
            String content = r.getContent();

            assertThat(content).contains("john.doe: 10");
            assertThat(content).contains("jane.smith: 6");
            assertThat(content).contains("Valuation: 16 units x Rs 4,000 = Rs 64,000");
        }

        @Test
        void valuationLineShowsPriceAndValuePerSpec() {
            when(medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, tablet, 3, null)));
            stubInTransitEmpty();

            ReportResponse r = reportService.medicineStockValuation();

            assertThat(r.getContent()).contains("Tablet 25 mg (10 Tablets)");
            assertThat(r.getContent()).contains("john.doe: 3");
            assertThat(r.getContent()).contains("Valuation: 3 units x Rs 4,000 = Rs 12,000");
        }

        @Test
        void currentValuationShowsInTransitBreakdownForActiveTransit() {
            // john has 12 total (4 settled + 8 in-transit)
            when(medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 12, null)));

            // Active in-transit adjustment
            MedicineStockAdjustment adj = MedicineStockAdjustment.builder()
                    .id(99L).user(john).medicine(vial).quantity(BigDecimal.valueOf(8))
                    .adjustmentType("ADD").inTransit(true).wasInTransit(true)
                    .transitDays(3).internalMovement(false)
                    .medicineStockType(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK)
                    .adjustedAt(LocalDateTime.now().minusHours(2))
                    .build();
            when(medicineStockAdjustmentRepository.findAllActiveInTransit()).thenReturn(List.of(adj));

            ReportResponse r = reportService.medicineStockValuation();

            assertThat(r.getContent()).contains("john.doe: 4 + 8 (in transit)");
            assertThat(r.getContent()).contains("Valuation: 12 units x Rs 4,000 = Rs 48,000");
        }

        @Test
        @DisplayName("valuation line is the last content line of each spec section — no TOTAL or Price lines")
        void valuationLineIsLastLineOfSpecSection() {
            when(medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 10, null)));
            when(medicineStockAdjustmentRepository.findAllActiveInTransit()).thenReturn(List.of());

            ReportResponse r = reportService.medicineStockValuation();
            String content = r.getContent();

            assertThat(content).contains("Valuation: 10 units x Rs 4,000 = Rs 40,000");
            assertThat(content).doesNotContain("  TOTAL:");
            assertThat(content).doesNotContain("Price: Rs");
        }

        @Test
        @DisplayName("valuation calculation uses total quantity including in-transit stock")
        void valuationUsesTotalQtyIncludingTransit() {
            when(medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(makeInv(1L, john, vial, 12, null)));

            MedicineStockAdjustment adj = MedicineStockAdjustment.builder()
                    .id(99L).user(john).medicine(vial).quantity(BigDecimal.valueOf(8))
                    .adjustmentType("ADD").inTransit(true).wasInTransit(true)
                    .transitDays(3).internalMovement(false)
                    .medicineStockType(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK)
                    .adjustedAt(LocalDateTime.now().minusHours(2))
                    .build();
            when(medicineStockAdjustmentRepository.findAllActiveInTransit()).thenReturn(List.of(adj));

            ReportResponse r = reportService.medicineStockValuation();

            assertThat(r.getContent()).contains("john.doe: 4 + 8 (in transit)");
            assertThat(r.getContent()).contains("Valuation: 12 units x Rs 4,000 = Rs 48,000");
        }

        @Test
        @DisplayName("multiple specs each have their own valuation line")
        void multipleSpecsEachHaveValuationLine() {
            when(medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(
                            makeInv(1L, john, vial, 5, null),
                            makeInv(2L, jane, tablet, 2, null)));
            when(medicineStockAdjustmentRepository.findAllActiveInTransit()).thenReturn(List.of());

            ReportResponse r = reportService.medicineStockValuation();
            String content = r.getContent();

            assertThat(content).contains("Valuation: 5 units x Rs 4,000 = Rs 20,000");
            assertThat(content).contains("Valuation: 2 units x Rs 4,000 = Rs 8,000");
        }

        @Test
        @DisplayName("medicineStock records with NULL medicineStockType (legacy rows) are included in valuation via findAllNonZeroRegularForValuation")
        void nullMedicineStockTypeRecordsIncludedViaRegularQuery() {
            // Simulate a legacy MedicineStock row where medicineStockType is NULL in DB.
            // MedicineStockTypeConverter maps NULL→REGULAR_MEDICINE_STOCK at the Java level,
            // but the old findAllNonZeroForValuation query (WHERE medicineStockType = :type) would
            // silently exclude these rows. findAllNonZeroRegularForValuation adds OR IS NULL.
            MedicineStock nullTypeInv = new MedicineStock();
            nullTypeInv.setId(10L); nullTypeInv.setUser(john); nullTypeInv.setMedicine(vial);
            nullTypeInv.setQuantity(BigDecimal.valueOf(7)); nullTypeInv.setMedicineStockType(null);
            nullTypeInv.setLastUpdated(java.time.LocalDateTime.now());

            when(medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(nullTypeInv));
            when(medicineStockAdjustmentRepository.findAllActiveInTransit()).thenReturn(List.of());

            ReportResponse r = reportService.medicineStockValuation();

            assertThat(r.getContent()).contains("john.doe: 7");
            assertThat(r.getContent()).contains("Valuation: 7 units x Rs 4,000 = Rs 28,000");
        }
    }

    @Nested @DisplayName("medicineStockValuation — historical date")
    class MedicineStockValuationHistorical {

        private MedicineStockAdjustment makeAdj(User u, Medicine m, MedicineStock.MedicineStockType type,
                                             String adjType, int qty, LocalDateTime at,
                                             boolean inTransit, boolean wasInTransit, int transitDays) {
            return MedicineStockAdjustment.builder()
                    .id(100L).user(u).medicine(m).quantity(BigDecimal.valueOf(qty))
                    .adjustmentType(adjType).note("test").inTransit(inTransit).wasInTransit(wasInTransit)
                    .transitDays(transitDays).internalMovement(false)
                    .medicineStockType(type).adjustedAt(at)
                    .build();
        }

        private MedicineStockAdjustment makeRegularAdj(User u, Medicine m, String adjType, int qty, LocalDateTime at) {
            return makeAdj(u, m, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, adjType, qty, at, false, false, 2);
        }

        private MedicineStockAdjustment makeInTransitAdj(User u, Medicine m, int qty, LocalDateTime at,
                                                      int transitDays, boolean stillActiveInDb) {
            return makeAdj(u, m, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, "ADD", qty, at,
                    stillActiveInDb, true, transitDays);
        }

        private Transaction makeApprovedTx(Long id, User u, Medicine m, int qty,
                                            MedicineStock.MedicineStockType type, LocalDateTime approvedAt) {
            Transaction tx = Transaction.builder()
                    .id(id).submittedBy(u).medicine(m).quantity(BigDecimal.valueOf(qty))
                    .status(Transaction.TransactionStatus.APPROVED).notes("test")
                    .medicineStockType(type)
                    .submittedAt(approvedAt).build();
            tx.setApprovedAt(approvedAt);
            return tx;
        }

        private void stubEmpty(LocalDate date) {
            when(medicineStockAdjustmentRepository.findAllUpTo(date.plusDays(1).atStartOfDay())).thenReturn(List.of());
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
        }

        @Test
        @DisplayName("historical report contains MEDICINE STOCK VALUATION header")
        void historicalReportContainsMedicineStockValuationHeader() {
            LocalDate date = LocalDate.of(2026, 5, 1);
            stubEmpty(date);

            ReportResponse r = reportService.medicineStockValuation(date);

            assertThat(r.getContent()).contains("MEDICINE STOCK VALUATION");
            assertThat(r.getReportType()).isEqualTo("MEDICINE_STOCK_VALUATION");
        }

        @Test
        @DisplayName("historical report shows As of: date line")
        void historicalReportShowsAsOfDateLine() {
            LocalDate date = LocalDate.of(2026, 5, 1);
            stubEmpty(date);

            ReportResponse r = reportService.medicineStockValuation(date);

            assertThat(r.getContent()).contains("As of: 01 May 2026");
        }

        @Test
        @DisplayName("historical report shows zero stock when no adjustments exist before target date")
        void historicalReportShowsZeroWhenNoPreDateAdjustments() {
            LocalDate date = LocalDate.of(2026, 5, 1);
            stubEmpty(date);

            ReportResponse r = reportService.medicineStockValuation(date);

            assertThat(r.getContent()).contains("TOTAL VALUATION: Rs 0");
        }

        @Test
        @DisplayName("historical report accumulates pre-date ADD adjustments into stock")
        void historicalReportAccumulatesPreDateAddAdjustments() {
            LocalDate date = LocalDate.of(2026, 5, 1);
            LocalDateTime preDateAt = LocalDateTime.of(2026, 4, 30, 9, 0);
            when(medicineStockAdjustmentRepository.findAllUpTo(date.plusDays(1).atStartOfDay()))
                    .thenReturn(List.of(makeRegularAdj(john, vial, "ADD", 10, preDateAt)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.medicineStockValuation(date);

            assertThat(r.getContent()).contains("john.doe: 10");
            assertThat(r.getContent()).contains("Valuation: 10 units x Rs 4,000 = Rs 40,000");
        }

        @Test
        @DisplayName("historical report subtracts pre-date approved transactions (dispatches) from stock")
        void historicalReportSubtractsPreDateTransactions() {
            LocalDate date = LocalDate.of(2026, 5, 1);
            LocalDateTime preDateAt = LocalDateTime.of(2026, 4, 30, 9, 0);
            when(medicineStockAdjustmentRepository.findAllUpTo(date.plusDays(1).atStartOfDay()))
                    .thenReturn(List.of(makeRegularAdj(john, vial, "ADD", 10, preDateAt)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any()))
                    .thenReturn(List.of(makeApprovedTx(1L, john, vial, 3,
                            MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, preDateAt.plusHours(1))));

            ReportResponse r = reportService.medicineStockValuation(date);

            assertThat(r.getContent()).contains("john.doe: 7");
            assertThat(r.getContent()).contains("Valuation: 7 units x Rs 4,000 = Rs 28,000");
        }

        @Test
        @DisplayName("historical report subtracts pre-date REMOVE adjustments from stock")
        void historicalReportSubtractsPreDateRemoveAdjustments() {
            LocalDate date = LocalDate.of(2026, 5, 1);
            LocalDateTime preDateAt = LocalDateTime.of(2026, 4, 30, 9, 0);
            when(medicineStockAdjustmentRepository.findAllUpTo(date.plusDays(1).atStartOfDay()))
                    .thenReturn(List.of(
                            makeRegularAdj(john, vial, "ADD", 10, preDateAt),
                            makeRegularAdj(john, vial, "REMOVE", 4, preDateAt.plusHours(1))
                    ));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.medicineStockValuation(date);

            assertThat(r.getContent()).contains("john.doe: 6");
            assertThat(r.getContent()).contains("Valuation: 6 units x Rs 4,000 = Rs 24,000");
        }

        @Test
        @DisplayName("historical report shows zero when no pre-date adjustments exist (all stock arrived later)")
        void historicalReportIsZeroWhenNoPreDateAdjustments() {
            LocalDate date = LocalDate.of(2025, 1, 1);
            stubEmpty(date);

            ReportResponse r = reportService.medicineStockValuation(date);

            assertThat(r.getContent()).contains("TOTAL VALUATION: Rs 0");
        }

        @Test
        @DisplayName("historical report excludes ADMIN_MEDICINE_STOCK adjustments from REGULAR totals")
        void historicalReportExcludesAdminAdjustments() {
            LocalDate date = LocalDate.of(2026, 5, 1);
            LocalDateTime preDateAt = LocalDateTime.of(2026, 4, 30, 9, 0);
            when(medicineStockAdjustmentRepository.findAllUpTo(date.plusDays(1).atStartOfDay()))
                    .thenReturn(List.of(
                            makeRegularAdj(john, vial, "ADD", 10, preDateAt),
                            makeAdj(john, vial, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK,
                                    "ADD", 5, preDateAt, false, false, 2)
                    ));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.medicineStockValuation(date);

            assertThat(r.getContent()).contains("TOTAL VALUATION: Rs 40,000");
        }

        @Test
        @DisplayName("historical report grand total covers multiple specs")
        void historicalReportGrandTotalIsCorrect() {
            // john: 5 vials (price 4000) = 20000; jane: 2 tablets 25mg (price 4000) = 8000; total = 28000
            LocalDate date = LocalDate.of(2026, 5, 1);
            LocalDateTime preDateAt = LocalDateTime.of(2026, 4, 30, 9, 0);
            when(medicineStockAdjustmentRepository.findAllUpTo(date.plusDays(1).atStartOfDay()))
                    .thenReturn(List.of(
                            makeRegularAdj(john, vial, "ADD", 5, preDateAt),
                            makeRegularAdj(jane, tablet, "ADD", 2, preDateAt)
                    ));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.medicineStockValuation(date);

            assertThat(r.getContent()).contains("TOTAL VALUATION: Rs 28,000");
        }

        @Test
        @DisplayName("no-arg medicineStockValuation() delegates to current (not historical)")
        void noArgDelegatesToCurrent() {
            when(medicineStockRepository.findAllNonZeroRegularForValuation(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of());
            when(medicineStockAdjustmentRepository.findAllActiveInTransit()).thenReturn(List.of());

            ReportResponse r = reportService.medicineStockValuation();

            assertThat(r.getReportType()).isEqualTo("MEDICINE_STOCK_VALUATION");
            assertThat(r.getContent()).contains("MEDICINE STOCK VALUATION");
            assertThat(r.getContent()).doesNotContain("As of:");
        }

        @Test
        @DisplayName("historical: pre-date in-transit ADD still in window shown as in-transit even if scheduler expired it")
        void historicalShowsInTransitWhenTransitActiveOnReportDate() {
            // Adjustment: June 3, 5 transit days → expires June 8.
            // Report date: June 5 → still in transit. DB inTransit=false (scheduler ran June 9) but wasInTransit=true.
            LocalDate reportDate = LocalDate.of(2026, 6, 5);
            LocalDateTime june3 = LocalDateTime.of(2026, 6, 3, 10, 0);

            when(medicineStockAdjustmentRepository.findAllUpTo(reportDate.plusDays(1).atStartOfDay()))
                    .thenReturn(List.of(makeInTransitAdj(john, vial, 8, june3, 5, false)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.medicineStockValuation(reportDate);

            assertThat(r.getContent()).contains("john.doe: 0 + 8 (in transit)");
        }

        @Test
        @DisplayName("historical: pre-date adjustment whose transit expired before report date shown as fully settled")
        void historicalShowsSettledWhenTransitExpiredBeforeReportDate() {
            // Adjustment: June 1, 2 transit days → expires June 3. Report date: June 5 → expired.
            LocalDate reportDate = LocalDate.of(2026, 6, 5);
            LocalDateTime june1 = LocalDateTime.of(2026, 6, 1, 10, 0);

            when(medicineStockAdjustmentRepository.findAllUpTo(reportDate.plusDays(1).atStartOfDay()))
                    .thenReturn(List.of(makeInTransitAdj(john, vial, 8, june1, 2, false)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.medicineStockValuation(reportDate);

            assertThat(r.getContent()).contains("john.doe: 8");
            assertThat(r.getContent()).doesNotContain("in transit");
        }

        @Test
        @DisplayName("historical: regular pre-date ADD (wasInTransit=false) never shown as in-transit")
        void historicalRegularAddNotShownAsInTransit() {
            LocalDate reportDate = LocalDate.of(2026, 6, 5);
            LocalDateTime june4 = LocalDateTime.of(2026, 6, 4, 10, 0);

            when(medicineStockAdjustmentRepository.findAllUpTo(reportDate.plusDays(1).atStartOfDay()))
                    .thenReturn(List.of(makeRegularAdj(john, vial, "ADD", 10, june4)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.medicineStockValuation(reportDate);

            assertThat(r.getContent()).contains("john.doe: 10");
            assertThat(r.getContent()).doesNotContain("in transit");
        }

        @Test
        @DisplayName("historical: legacy record with wasInTransit=false but inTransit=true shown as in-transit")
        void historicalShowsInTransitForLegacyRecordWhereWasInTransitNotSet() {
            LocalDate reportDate = LocalDate.of(2026, 6, 5);
            LocalDateTime june3 = LocalDateTime.of(2026, 6, 3, 10, 0);

            MedicineStockAdjustment legacyAdj = MedicineStockAdjustment.builder()
                    .id(200L).user(john).medicine(vial).quantity(BigDecimal.valueOf(6))
                    .adjustmentType("ADD").note("legacy").inTransit(true).wasInTransit(false)
                    .transitDays(5).internalMovement(false)
                    .medicineStockType(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK)
                    .adjustedAt(june3).build();
            when(medicineStockAdjustmentRepository.findAllUpTo(reportDate.plusDays(1).atStartOfDay()))
                    .thenReturn(List.of(legacyAdj));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.medicineStockValuation(reportDate);

            assertThat(r.getContent()).contains("john.doe: 0 + 6 (in transit)");
        }

        @Test
        @DisplayName("historical: valuation line is last content line of each spec section")
        void historicalValuationLineIsLastLineOfSpecSection() {
            LocalDate date = LocalDate.of(2026, 5, 1);
            LocalDateTime preDateAt = LocalDateTime.of(2026, 4, 30, 9, 0);
            when(medicineStockAdjustmentRepository.findAllUpTo(date.plusDays(1).atStartOfDay()))
                    .thenReturn(List.of(makeRegularAdj(john, vial, "ADD", 10, preDateAt)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.medicineStockValuation(date);

            assertThat(r.getContent()).contains("Valuation: 10 units x Rs 4,000 = Rs 40,000");
            assertThat(r.getContent()).doesNotContain("  TOTAL:");
            assertThat(r.getContent()).doesNotContain("Price: Rs");
        }

        @Test
        @DisplayName("historical: multiple specs each have their own valuation line")
        void historicalMultipleSpecsHaveValuationLine() {
            LocalDate date = LocalDate.of(2026, 5, 1);
            LocalDateTime preDateAt = LocalDateTime.of(2026, 4, 30, 9, 0);
            when(medicineStockAdjustmentRepository.findAllUpTo(date.plusDays(1).atStartOfDay()))
                    .thenReturn(List.of(
                            makeRegularAdj(john, vial, "ADD", 5, preDateAt),
                            makeRegularAdj(jane, tablet, "ADD", 2, preDateAt)
                    ));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.medicineStockValuation(date);
            String content = r.getContent();

            assertThat(content).contains("Valuation: 5 units x Rs 4,000 = Rs 20,000");
            assertThat(content).contains("Valuation: 2 units x Rs 4,000 = Rs 8,000");
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
        void adminDispatchExcludedFromSalesReport() {
            Transaction adminTx = Transaction.builder()
                    .id(1L).submittedBy(john).medicine(vial).quantity(BigDecimal.valueOf(3))
                    .status(Transaction.TransactionStatus.APPROVED).notes("admin dispatch")
                    .medicineStockType(MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK)
                    .submittedAt(LocalDateTime.now()).build();
            adminTx.setApprovedAt(LocalDateTime.now());
            Transaction regularTx = makeTx(2L, jane, tablet, 2,
                    Transaction.TransactionStatus.APPROVED, "regular dispatch");
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(adminTx, regularTx));

            ReportResponse r = reportService.todaySales();

            assertThat(r.getContent()).doesNotContain("john.doe  3 x 10 ml");
            assertThat(r.getContent()).contains("Jane Smith");
            assertThat(r.getContent()).contains("25 mg");
        }

        @Test
        void allAdminDispatchesMakeReportShowNoSales() {
            Transaction adminTx = Transaction.builder()
                    .id(1L).submittedBy(john).medicine(vial).quantity(BigDecimal.valueOf(5))
                    .status(Transaction.TransactionStatus.APPROVED).notes("admin only")
                    .medicineStockType(MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK)
                    .submittedAt(LocalDateTime.now()).build();
            adminTx.setApprovedAt(LocalDateTime.now());
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(adminTx));

            ReportResponse r = reportService.todaySales();

            assertThat(r.getContent()).contains("No sales recorded today");
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

        @Test
        void usernameFilterExcludesOtherUsersSales() {
            Transaction johnTx = makeTx(1L, john, vial, 2,
                    Transaction.TransactionStatus.APPROVED, "john's dispatch");
            Transaction janeTx = makeTx(2L, jane, tablet, 1,
                    Transaction.TransactionStatus.APPROVED, "jane's dispatch");
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(johnTx, janeTx));

            ReportResponse r = reportService.todaySales(null, null, "john.doe", null);

            assertThat(r.getContent()).contains("John Doe");
            assertThat(r.getContent()).doesNotContain("Jane Smith");
        }

        @Test
        void blankOrAllUsernameAppliesNoFilter() {
            Transaction johnTx = makeTx(1L, john, vial, 2,
                    Transaction.TransactionStatus.APPROVED, "john's dispatch");
            Transaction janeTx = makeTx(2L, jane, tablet, 1,
                    Transaction.TransactionStatus.APPROVED, "jane's dispatch");
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(johnTx, janeTx));

            ReportResponse all = reportService.todaySales(null, null, "ALL", null);
            ReportResponse blank = reportService.todaySales(null, null, "  ", null);

            assertThat(all.getContent()).contains("John Doe").contains("Jane Smith");
            assertThat(blank.getContent()).contains("John Doe").contains("Jane Smith");
        }

        @Test
        void medicineIdFilterExcludesOtherMedicinesSales() {
            Transaction vialTx = makeTx(1L, john, vial, 2,
                    Transaction.TransactionStatus.APPROVED, "vial dispatch");
            Transaction tabletTx = makeTx(2L, john, tablet, 1,
                    Transaction.TransactionStatus.APPROVED, "tablet dispatch");
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(vialTx, tabletTx));

            ReportResponse r = reportService.todaySales(null, null, null, vial.getId());

            assertThat(r.getContent()).contains("10 ml");
            assertThat(r.getContent()).doesNotContain("25 mg");
        }

        @Test
        void combinedUsernameAndMedicineIdFilterNarrowsToBoth() {
            Transaction johnVial = makeTx(1L, john, vial, 2,
                    Transaction.TransactionStatus.APPROVED, "john vial");
            Transaction johnTablet = makeTx(2L, john, tablet, 1,
                    Transaction.TransactionStatus.APPROVED, "john tablet");
            Transaction janeVial = makeTx(3L, jane, vial, 3,
                    Transaction.TransactionStatus.APPROVED, "jane vial");
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(johnVial, johnTablet, janeVial));

            ReportResponse r = reportService.todaySales(null, null, "john.doe", vial.getId());

            assertThat(r.getContent()).contains("john vial");
            assertThat(r.getContent()).doesNotContain("john tablet");
            assertThat(r.getContent()).doesNotContain("Jane Smith");
        }

        @Test
        void filtersMatchingNothingShowNoSalesMessage() {
            Transaction johnTx = makeTx(1L, john, vial, 2,
                    Transaction.TransactionStatus.APPROVED, "john's dispatch");
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(johnTx));

            ReportResponse r = reportService.todaySales(null, null, "jane.smith", null);

            assertThat(r.getContent()).contains("No sales recorded today");
        }
    }

    @Nested @DisplayName("dailyReport")
    class DailyReport {

        private static final LocalDateTime PAST = LocalDateTime.of(2026, 1, 1, 9, 0);

        private void stubEmpty() {
            when(medicineStockAdjustmentRepository.findAllUpTo(any())).thenReturn(List.of());
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());
        }

        private MedicineStockAdjustment makeRegularAdj(User u, Medicine m, MedicineStock.MedicineStockType type, int qty, LocalDateTime at) {
            return MedicineStockAdjustment.builder()
                    .id(200L).user(u).medicine(m).quantity(BigDecimal.valueOf(qty))
                    .adjustmentType("ADD").note("stock").inTransit(false).wasInTransit(false)
                    .transitDays(2).internalMovement(false)
                    .medicineStockType(type).adjustedAt(at)
                    .build();
        }

        private MedicineStockAdjustment makeTransitAdj(User u, Medicine m, MedicineStock.MedicineStockType type,
                                                    int qty, LocalDateTime at, int transitDays) {
            return MedicineStockAdjustment.builder()
                    .id(201L).user(u).medicine(m).quantity(BigDecimal.valueOf(qty))
                    .adjustmentType("ADD").note("shipment").inTransit(true).wasInTransit(true)
                    .transitDays(transitDays).internalMovement(false)
                    .medicineStockType(type).adjustedAt(at)
                    .build();
        }

        @Test
        void reportTypeAndHeader() {
            stubEmpty();

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getReportType()).isEqualTo("DAILY_REPORT");
            assertThat(r.getContent()).contains("DAILY REPORT");
            assertThat(r.getContent()).contains("REGULAR MEDICINE STOCK");
            assertThat(r.getContent()).contains("ADMIN MEDICINE STOCK");
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
        void pharmaNameIsUsedAsHeadingFromMedicineStockData() {
            when(medicineStockAdjustmentRepository.findAllUpTo(any()))
                    .thenReturn(List.of(makeRegularAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 10, PAST)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("Shield FX");
            assertThat(r.getContent()).doesNotContain("mg/ml");
            assertThat(r.getContent()).doesNotContain("Shield FX Vial");
        }

        @Test
        void pharmaNameAppearsUnderEachMedicineStockSection() {
            when(medicineStockAdjustmentRepository.findAllUpTo(any()))
                    .thenReturn(List.of(
                            makeRegularAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 10, PAST),
                            makeRegularAdj(john, vial, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK, 5, PAST)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(null);
            String content = r.getContent();

            int regularIdx = content.indexOf("REGULAR MEDICINE STOCK");
            int adminIdx   = content.indexOf("ADMIN MEDICINE STOCK");
            int txIdx      = content.indexOf("DAILY TRANSACTION SUMMARY");

            String regularBlock = content.substring(regularIdx, adminIdx);
            String adminBlock   = content.substring(adminIdx, txIdx);

            assertThat(regularBlock).contains("Shield FX");
            assertThat(adminBlock).contains("Shield FX");
        }

        @Test
        void vialAppearsBeforeTabletInFixedOrder() {
            when(medicineStockAdjustmentRepository.findAllUpTo(any()))
                    .thenReturn(List.of(
                            makeRegularAdj(john, vial,   MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 10, PAST),
                            makeRegularAdj(john, tablet, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 5,  PAST)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(null);

            int pos10ml = r.getContent().indexOf("Vial 10 ml");
            int pos25mg = r.getContent().indexOf("Tablet 25 mg");
            assertThat(pos10ml).isLessThan(pos25mg);
        }

        @Test
        void tenMlVialAppearsBeforeFiveMlVial() {
            when(medicineStockAdjustmentRepository.findAllUpTo(any()))
                    .thenReturn(List.of(
                            makeRegularAdj(john, vial,  MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 10, PAST),
                            makeRegularAdj(john, vial5, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 5,  PAST)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(null);

            int pos10ml = r.getContent().indexOf("Vial 10 ml");
            int pos5ml  = r.getContent().indexOf("Vial 5 ml");
            assertThat(pos10ml).isGreaterThanOrEqualTo(0);
            assertThat(pos10ml).isLessThan(pos5ml);
        }

        @Test
        void twoBlankLinesSeparateOneManufacturersSpecsFromTheNext() {
            PharmaCompany otherPharma = new PharmaCompany();
            otherPharma.setId(2L); otherPharma.setName("Acme Labs");
            Medicine otherVial = new Medicine();
            otherVial.setId(20L); otherVial.setName("Acme Labs Vial 10 ml");
            otherVial.setType(Medicine.MedicineType.VIAL); otherVial.setSpecification(10.0);
            otherVial.setConcentrationMgPerMl(20.0);
            otherVial.setPrice(4000); otherVial.setPharmaCompany(otherPharma);

            when(medicineStockAdjustmentRepository.findAllUpTo(any()))
                    .thenReturn(List.of(
                            makeRegularAdj(john, vial,      MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 10, PAST),
                            makeRegularAdj(john, otherVial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 5,  PAST)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(null);

            int endOfShieldFxBlock = r.getContent().indexOf("TOTAL: 10") + "TOTAL: 10".length();
            int startOfAcmeHeading = r.getContent().indexOf("Acme Labs");
            String gap = r.getContent().substring(endOfShieldFxBlock, startOfAcmeHeading);
            assertThat(gap).isEqualTo("\n\n\n");
        }

        @Test
        void showsUserQuantityUnderSpec() {
            when(medicineStockAdjustmentRepository.findAllUpTo(any()))
                    .thenReturn(List.of(makeRegularAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 30, PAST)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("john.doe: 30");
            assertThat(r.getContent()).contains("TOTAL: 30");
        }

        @Test
        void specsWithZeroMedicineStockAreNotShown() {
            // Only vial 10 ml has stock — other specs must be absent from the report
            when(medicineStockAdjustmentRepository.findAllUpTo(any()))
                    .thenReturn(List.of(makeRegularAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 10, PAST)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("Vial 10 ml");
            assertThat(r.getContent()).contains("john.doe: 10");
            assertThat(r.getContent()).doesNotContain("Vial 5 ml");
            assertThat(r.getContent()).doesNotContain("Tablet");
            assertThat(r.getContent()).doesNotContain("(none)");
            assertThat(r.getContent()).doesNotContain("TOTAL: 0");
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
        void regularTransactionAppearsUnderRegularStockSection() {
            stubEmpty();
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(makeTx(1L, john, vial, 3,
                            Transaction.TransactionStatus.APPROVED, "Clinic B")));

            ReportResponse r = reportService.dailyReport(null);
            String content = r.getContent();

            assertThat(content).contains("Regular Stock Transactions");
            assertThat(content).contains("john.doe  3 x 10 ml  Clinic B");
            assertThat(content).doesNotContain("Admin Stock Transactions");
        }

        @Test
        void adminTransactionAppearsUnderAdminStockSection() {
            stubEmpty();
            Transaction adminTx = Transaction.builder()
                    .id(1L).submittedBy(john).medicine(vial).quantity(BigDecimal.valueOf(2))
                    .status(Transaction.TransactionStatus.APPROVED).notes("emergency")
                    .medicineStockType(MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK)
                    .submittedAt(LocalDateTime.now()).build();
            adminTx.setApprovedAt(LocalDateTime.now());
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(adminTx));

            ReportResponse r = reportService.dailyReport(null);
            String content = r.getContent();

            assertThat(content).contains("Admin Stock Transactions");
            assertThat(content).contains("john.doe  2 x 10 ml  emergency");
            assertThat(content).doesNotContain("Regular Stock Transactions");
        }

        @Test
        void bothSubsectionsPresentWhenBothTypesExist() {
            stubEmpty();
            Transaction regularTx = makeTx(1L, john, vial, 3,
                    Transaction.TransactionStatus.APPROVED, "clinic");
            Transaction adminTx = Transaction.builder()
                    .id(2L).submittedBy(jane).medicine(tablet).quantity(BigDecimal.ONE)
                    .status(Transaction.TransactionStatus.APPROVED).notes("admin")
                    .medicineStockType(MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK)
                    .submittedAt(LocalDateTime.now()).build();
            adminTx.setApprovedAt(LocalDateTime.now());
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(regularTx, adminTx));

            ReportResponse r = reportService.dailyReport(null);
            String content = r.getContent();

            assertThat(content).contains("Regular Stock Transactions");
            assertThat(content).contains("Admin Stock Transactions");
            int posRegular = content.indexOf("Regular Stock Transactions");
            int posAdmin   = content.indexOf("Admin Stock Transactions");
            assertThat(posRegular).isLessThan(posAdmin);
        }

        @Test
        void regularSubsectionAbsentWhenOnlyAdminTransactionsExist() {
            stubEmpty();
            Transaction adminTx = Transaction.builder()
                    .id(1L).submittedBy(john).medicine(vial).quantity(BigDecimal.valueOf(4))
                    .status(Transaction.TransactionStatus.APPROVED).notes("admin only")
                    .medicineStockType(MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK)
                    .submittedAt(LocalDateTime.now()).build();
            adminTx.setApprovedAt(LocalDateTime.now());
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(adminTx));

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).doesNotContain("Regular Stock Transactions");
            assertThat(r.getContent()).contains("Admin Stock Transactions");
        }

        @Test
        void multipleUsersForSameSpecShowSeparateLines() {
            when(medicineStockAdjustmentRepository.findAllUpTo(any()))
                    .thenReturn(List.of(
                            makeRegularAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 30, PAST),
                            makeRegularAdj(jane, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 20, PAST)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("john.doe: 30");
            assertThat(r.getContent()).contains("jane.smith: 20");
            assertThat(r.getContent()).contains("TOTAL: 50");
        }

        @Test
        void adminMedicineStockSectionPresentBeforeTransactions() {
            stubEmpty();

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("ADMIN MEDICINE STOCK");
            int posAdmin = r.getContent().indexOf("ADMIN MEDICINE STOCK");
            int posTx    = r.getContent().indexOf("DAILY TRANSACTION SUMMARY");
            assertThat(posAdmin).isLessThan(posTx);
        }

        @Test
        void adminMedicineStockShowsCorrectQuantity() {
            // ADMIN_STOCK medicine stock belongs to non-admin users in the new model
            when(medicineStockAdjustmentRepository.findAllUpTo(any()))
                    .thenReturn(List.of(makeRegularAdj(john, vial, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK, 15, PAST)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
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
        void adminMedicineStockSkipsSpecsWithNoAdminStock() {
            // Feature 2: admin medicine stock section skips specs with no data (no (none)/TOTAL: 0)
            when(medicineStockAdjustmentRepository.findAllUpTo(any()))
                    .thenReturn(List.of(makeRegularAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 10, PAST)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
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
        void adminMedicineStockShowsOnlySpecsWithData() {
            // Only vial 10 ml has admin stock — other specs should not appear
            when(medicineStockAdjustmentRepository.findAllUpTo(any()))
                    .thenReturn(List.of(makeRegularAdj(john, vial, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK, 8, PAST)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
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
        void adminMedicineStockDoesNotIncludeRegularMedicineStock() {
            when(medicineStockAdjustmentRepository.findAllUpTo(any()))
                    .thenReturn(List.of(
                            makeRegularAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 30, PAST),
                            makeRegularAdj(john, vial, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK, 5, PAST)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
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

    // ── In-Transit Display ────────────────────────────────────────────────────

    @Nested @DisplayName("In-Transit — dailyReport")
    class InTransitDailyReport {

        private void stubEmptyTransactions() {
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());
        }

        private MedicineStockAdjustment makeRegularAdj(User u, Medicine m, MedicineStock.MedicineStockType type, int qty, LocalDateTime at) {
            return MedicineStockAdjustment.builder()
                    .id(200L).user(u).medicine(m).quantity(BigDecimal.valueOf(qty))
                    .adjustmentType("ADD").note("stock").inTransit(false).wasInTransit(false)
                    .transitDays(2).internalMovement(false)
                    .medicineStockType(type).adjustedAt(at)
                    .build();
        }

        private MedicineStockAdjustment makeTransitAdj(User u, Medicine m, MedicineStock.MedicineStockType type,
                                                    int qty, LocalDateTime at, int transitDays) {
            return MedicineStockAdjustment.builder()
                    .id(201L).user(u).medicine(m).quantity(BigDecimal.valueOf(qty))
                    .adjustmentType("ADD").note("shipment").inTransit(true).wasInTransit(true)
                    .transitDays(transitDays).internalMovement(false)
                    .medicineStockType(type).adjustedAt(at)
                    .build();
        }

        @Test
        void inTransitStockShowsSettledPlusTransitFormat() {
            // john has 15 total: 10 settled (regular ADD) + 5 in-transit (transit ADD)
            LocalDateTime now = LocalDateTime.now().minusHours(1);
            when(medicineStockAdjustmentRepository.findAllUpTo(any()))
                    .thenReturn(List.of(
                            makeRegularAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 10, now.minusDays(1)),
                            makeTransitAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 5, now, 2)));
            stubEmptyTransactions();

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("john.doe: 10 + 5 (in transit)");
        }

        @Test
        void inTransitTotalStillIncludesTransitAmount() {
            // john: 10 settled + 5 in-transit = 15 total; jane: 8 settled
            LocalDateTime now = LocalDateTime.now();
            when(medicineStockAdjustmentRepository.findAllUpTo(any()))
                    .thenReturn(List.of(
                            makeRegularAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 10, now.minusDays(2)),
                            makeTransitAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 5,  now.minusHours(1), 2),
                            makeRegularAdj(jane, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 8,  now.minusDays(2))));
            stubEmptyTransactions();

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("john.doe: 10 + 5 (in transit)");
            assertThat(r.getContent()).contains("jane.smith: 8");
            assertThat(r.getContent()).contains("TOTAL: 23");
        }

        @Test
        void expiredInTransitNotShownAsTransit() {
            // transit ADD 3 days ago with transitDays=2 → expired; settled regular ADD of 10
            LocalDateTime threeAgo = LocalDateTime.now().minusDays(3);
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            when(medicineStockAdjustmentRepository.findAllUpTo(any()))
                    .thenReturn(List.of(
                            makeRegularAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 10, yesterday),
                            makeTransitAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 5, threeAgo, 2)));
            stubEmptyTransactions();

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("john.doe: 15");
            assertThat(r.getContent()).doesNotContain("in transit");
        }

        @Test
        void noInTransitAdjustmentsShowsNormalFormat() {
            when(medicineStockAdjustmentRepository.findAllUpTo(any()))
                    .thenReturn(List.of(makeRegularAdj(john, tablet, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 10, LocalDateTime.now().minusDays(1))));
            stubEmptyTransactions();

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("john.doe: 10");
            assertThat(r.getContent()).doesNotContain("in transit");
        }

        @Test
        void inTransitShownForAdminStockSection() {
            // john admin: 12 settled + 8 in-transit = 20 total
            LocalDateTime now = LocalDateTime.now().minusHours(2);
            when(medicineStockAdjustmentRepository.findAllUpTo(any()))
                    .thenReturn(List.of(
                            makeRegularAdj(john, vial, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK, 12, now.minusDays(1)),
                            makeTransitAdj(john, vial, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK, 8, now, 2)));
            stubEmptyTransactions();

            ReportResponse r = reportService.dailyReport(null);

            String content = r.getContent();
            int adminIdx = content.indexOf("ADMIN MEDICINE STOCK");
            String adminBlock = content.substring(adminIdx);
            assertThat(adminBlock).contains("john.doe: 12 + 8 (in transit)");
        }

        @Test
        void multipleInTransitAdjustmentsSummedForSameUserMedicine() {
            // john: 10 settled + 3 transit + 5 transit = 18 total, 8 in transit
            LocalDateTime now = LocalDateTime.now();
            when(medicineStockAdjustmentRepository.findAllUpTo(any()))
                    .thenReturn(List.of(
                            makeRegularAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 10, now.minusDays(2)),
                            makeTransitAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 3, now.minusHours(1), 2),
                            makeTransitAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 5, now.minusHours(2), 2)));
            stubEmptyTransactions();

            ReportResponse r = reportService.dailyReport(null);

            assertThat(r.getContent()).contains("john.doe: 10 + 8 (in transit)");
        }

        @Test
        @DisplayName("adjustment made AFTER report date is excluded from both stock and in-transit")
        void adjustmentAfterReportDateNotShownAsInTransit() {
            // Report for June 5; june6 adj is NOT in findAllUpTo(june6-midnight).
            // john's settled stock comes from a pre-date regular ADD.
            LocalDate reportDate = LocalDate.of(2026, 6, 5);
            LocalDateTime june4 = LocalDateTime.of(2026, 6, 4, 9, 0);
            when(medicineStockAdjustmentRepository.findAllUpTo(reportDate.plusDays(1).atStartOfDay()))
                    .thenReturn(List.of(makeRegularAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 15, june4)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(reportDate);

            assertThat(r.getContent()).doesNotContain("in transit");
            assertThat(r.getContent()).contains("john.doe: 15");
        }

        @Test
        @DisplayName("adjustment made ON the report date IS shown as in-transit")
        void adjustmentOnReportDateIsShownAsInTransit() {
            // Report for June 6; regular 7 (june5) + transit 8 (june6 09:00) = 15 total, 8 in transit
            LocalDate reportDate = LocalDate.of(2026, 6, 6);
            LocalDateTime june5 = LocalDateTime.of(2026, 6, 5, 9, 0);
            LocalDateTime june6Morning = LocalDateTime.of(2026, 6, 6, 9, 0);
            when(medicineStockAdjustmentRepository.findAllUpTo(reportDate.plusDays(1).atStartOfDay()))
                    .thenReturn(List.of(
                            makeRegularAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 7, june5),
                            makeTransitAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 8, june6Morning, 3)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(reportDate);

            assertThat(r.getContent()).contains("john.doe: 7 + 8 (in transit)");
        }

        @Test
        @DisplayName("adjustment at midnight of NEXT day is excluded (boundary: adjustedAt == end)")
        void adjustmentAtMidnightOfNextDayExcludedFromReport() {
            // Report for June 5; adj at june6 midnight is NOT before june6 midnight → excluded.
            // john's stock comes from a pre-date regular ADD of 15.
            LocalDate reportDate = LocalDate.of(2026, 6, 5);
            LocalDateTime june5_9am = LocalDateTime.of(2026, 6, 5, 9, 0);
            when(medicineStockAdjustmentRepository.findAllUpTo(reportDate.plusDays(1).atStartOfDay()))
                    .thenReturn(List.of(makeRegularAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 15, june5_9am)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(reportDate);

            assertThat(r.getContent()).doesNotContain("in transit");
            assertThat(r.getContent()).contains("john.doe: 15");
        }

        @Test
        @DisplayName("adjustment at last moment of report date IS shown as in-transit")
        void adjustmentAtLastMomentOfReportDateShownAsInTransit() {
            // Report for June 5; regular 7 (june5 09:00) + transit 8 (june5 23:59:59) = 15, 8 in transit
            LocalDate reportDate = LocalDate.of(2026, 6, 5);
            LocalDateTime june5_9am  = LocalDateTime.of(2026, 6, 5, 9, 0);
            LocalDateTime june5End   = LocalDateTime.of(2026, 6, 5, 23, 59, 59);
            when(medicineStockAdjustmentRepository.findAllUpTo(reportDate.plusDays(1).atStartOfDay()))
                    .thenReturn(List.of(
                            makeRegularAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 7, june5_9am),
                            makeTransitAdj(john, vial, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 8, june5End, 3)));
            when(transactionRepository.findNonRejectedSubmittedUpTo(any(), any())).thenReturn(List.of());
            when(transactionRepository.findApprovedBetween(any(), any(), any())).thenReturn(List.of());

            ReportResponse r = reportService.dailyReport(reportDate);

            assertThat(r.getContent()).contains("john.doe: 7 + 8 (in transit)");
        }
    }

    @Nested @DisplayName("In-Transit — medicineStockByUser")
    class InTransitMedicineStockByUser {

        private MedicineStockAdjustment makeInTransitAdj(User u, Medicine m,
                                                     MedicineStock.MedicineStockType type, int qty, LocalDateTime at) {
            return MedicineStockAdjustment.builder()
                    .user(u).medicine(m).medicineStockType(type).quantity(BigDecimal.valueOf(qty)).adjustedAt(at)
                    .adjustmentType("ADD").note("transit").inTransit(true).wasInTransit(true)
                    .transitDays(2).internalMovement(false)
                    .build();
        }

        @Test
        void inTransitShownInMedicineStockByUserReport() {
            MedicineStock inv = makeInv(1L, john, tablet, 15, null);
            when(medicineStockRepository.findAllNonZeroRegularOrderByMedicineAndUser(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(inv));
            when(medicineStockRepository.findAllNonZeroOrderByMedicineAndUser(MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of());

            MedicineStockAdjustment adj = makeInTransitAdj(john, tablet,
                    MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 5, LocalDateTime.now().minusHours(1));
            when(medicineStockAdjustmentRepository.findAllActiveInTransit())
                    .thenReturn(List.of(adj));

            ReportResponse r = reportService.medicineStockByUser();

            assertThat(r.getContent()).contains("john.doe: 10 + 5 (in transit)");
        }

        @Test
        void inTransitTotalIncludesTransitInMedicineStockByUser() {
            MedicineStock inv = makeInv(1L, john, vial, 12, null);
            when(medicineStockRepository.findAllNonZeroRegularOrderByMedicineAndUser(MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK))
                    .thenReturn(List.of(inv));
            when(medicineStockRepository.findAllNonZeroOrderByMedicineAndUser(MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK))
                    .thenReturn(List.of());

            MedicineStockAdjustment adj = makeInTransitAdj(john, vial,
                    MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 2, LocalDateTime.now().minusHours(3));
            when(medicineStockAdjustmentRepository.findAllActiveInTransit())
                    .thenReturn(List.of(adj));

            ReportResponse r = reportService.medicineStockByUser();

            assertThat(r.getContent()).contains("john.doe: 10 + 2 (in transit)");
            assertThat(r.getContent()).contains("TOTAL: 12");
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // SalesGraph
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SalesGraph")
    class SalesGraph {

        private Transaction makeTx(Long id, User u, Medicine m, int qty, LocalDateTime at) {
            return makeTx(id, u, m, qty, at, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
        }

        private Transaction makeTx(Long id, User u, Medicine m, int qty, LocalDateTime at,
                                    MedicineStock.MedicineStockType stockType) {
            Transaction tx = Transaction.builder()
                    .id(id).submittedBy(u).medicine(m).quantity(BigDecimal.valueOf(qty))
                    .status(Transaction.TransactionStatus.APPROVED).notes("t")
                    .medicineStockType(stockType)
                    .submittedAt(at).build();
            tx.setApprovedAt(at);
            return tx;
        }

        @Test
        @DisplayName("daily period produces one data point per day with correct label")
        void dailyPeriodLabelFormat() {
            LocalDateTime at = LocalDateTime.of(2026, 6, 15, 10, 0);
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(makeTx(1L, john, vial, 5, at)));

            SalesGraphResponse resp = reportService.salesGraph(
                    "daily", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15), null);

            assertThat(resp.getPeriod()).isEqualTo("daily");
            assertThat(resp.getDataPoints()).hasSize(1);
            assertThat(resp.getDataPoints().get(0).getLabel()).isEqualTo("15 Jun");
        }

        @Test
        @DisplayName("each bar has spec breakdowns matching DAILY_SPEC_ORDER names")
        void barsContainSpecBreakdowns() {
            LocalDateTime at = LocalDateTime.of(2026, 6, 15, 10, 0);
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(
                            makeTx(1L, john, vial,   3, at),
                            makeTx(2L, jane, vial5,  2, at)
                    ));

            SalesGraphResponse resp = reportService.salesGraph(
                    "daily", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15), null);

            var dp = resp.getDataPoints().get(0);
            assertThat(dp.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
            assertThat(dp.getSpecs()).isNotEmpty();

            var vial10Spec = dp.getSpecs().stream()
                    .filter(s -> "Vial 10 ml".equals(s.getSpecName())).findFirst();
            assertThat(vial10Spec).isPresent();
            assertThat(vial10Spec.get().getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(3));

            var vial5Spec = dp.getSpecs().stream()
                    .filter(s -> "Vial 5 ml".equals(s.getSpecName())).findFirst();
            assertThat(vial5Spec).isPresent();
            assertThat(vial5Spec.get().getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(2));
        }

        @Test
        @DisplayName("total quantity equals sum of all spec quantities")
        void totalQuantityEqualsSumOfSpecs() {
            LocalDateTime at = LocalDateTime.of(2026, 6, 15, 10, 0);
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(
                            makeTx(1L, john, vial,   3, at),
                            makeTx(2L, jane, vial5,  7, at),
                            makeTx(3L, john, tablet, 4, at)
                    ));

            SalesGraphResponse resp = reportService.salesGraph(
                    "daily", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15), null);

            var dp = resp.getDataPoints().get(0);
            BigDecimal specSum = dp.getSpecs().stream()
                    .map(SalesGraphResponse.SpecBreakdown::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(dp.getQuantity()).isEqualByComparingTo(specSum).isEqualByComparingTo(BigDecimal.valueOf(14));
        }

        @Test
        @DisplayName("total value equals sum of all spec values")
        void totalValueEqualsSumOfSpecs() {
            LocalDateTime at = LocalDateTime.of(2026, 6, 15, 10, 0);
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(
                            makeTx(1L, john, vial,  2, at),   // 2 * 4000 = 8000
                            makeTx(2L, jane, vial5, 3, at)    // 3 * 3000 = 9000
                    ));

            SalesGraphResponse resp = reportService.salesGraph(
                    "daily", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15), null);

            var dp = resp.getDataPoints().get(0);
            long specValueSum = dp.getSpecs().stream()
                    .mapToLong(SalesGraphResponse.SpecBreakdown::getValue).sum();
            assertThat(dp.getValue()).isEqualTo(specValueSum).isEqualTo(17000L);
        }

        @Test
        @DisplayName("pricePerUnit overrides medicine price in spec value calculation")
        void pricePerUnitOverridesMedicinePrice() {
            LocalDateTime at = LocalDateTime.of(2026, 6, 15, 10, 0);
            Transaction tx = makeTx(1L, john, vial, 2, at);
            tx.setPricePerUnit(5000);
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(tx));

            SalesGraphResponse resp = reportService.salesGraph(
                    "daily", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15), null);

            var dp = resp.getDataPoints().get(0);
            assertThat(dp.getValue()).isEqualTo(10000L);
            var spec = dp.getSpecs().stream()
                    .filter(s -> "Vial 10 ml".equals(s.getSpecName())).findFirst().orElseThrow();
            assertThat(spec.getValue()).isEqualTo(10000L);
        }

        @Test
        @DisplayName("gap-fills empty days with zero-quantity data points")
        void gapFillsEmptyDays() {
            LocalDateTime at = LocalDateTime.of(2026, 6, 15, 10, 0);
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(makeTx(1L, john, vial, 5, at)));

            SalesGraphResponse resp = reportService.salesGraph(
                    "daily", LocalDate.of(2026, 6, 14), LocalDate.of(2026, 6, 16), null);

            assertThat(resp.getDataPoints()).hasSize(3);
            assertThat(resp.getDataPoints().get(0).getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);  // 14 Jun
            assertThat(resp.getDataPoints().get(1).getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));  // 15 Jun
            assertThat(resp.getDataPoints().get(2).getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);  // 16 Jun
        }

        @Test
        @DisplayName("weekly grouping merges Mon-Sun into single bar starting Monday")
        void weeklyGroupingUsesMondayStart() {
            // June 15, 2026 = Monday; June 17, 2026 = Wednesday (same week)
            LocalDateTime mon = LocalDateTime.of(2026, 6, 15, 10, 0);
            LocalDateTime wed = LocalDateTime.of(2026, 6, 17, 10, 0);
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(
                            makeTx(1L, john, vial, 3, mon),
                            makeTx(2L, jane, vial, 2, wed)
                    ));

            SalesGraphResponse resp = reportService.salesGraph(
                    "weekly", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 21), null);

            assertThat(resp.getDataPoints()).hasSize(1);
            assertThat(resp.getDataPoints().get(0).getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
        }

        @Test
        @DisplayName("monthly grouping groups all days in same month into one bar")
        void monthlyGroupsAllDaysInMonth() {
            LocalDateTime d1 = LocalDateTime.of(2026, 6, 1,  10, 0);
            LocalDateTime d2 = LocalDateTime.of(2026, 6, 20, 10, 0);
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(
                            makeTx(1L, john, vial, 4, d1),
                            makeTx(2L, jane, vial, 6, d2)
                    ));

            SalesGraphResponse resp = reportService.salesGraph(
                    "monthly", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null);

            assertThat(resp.getDataPoints()).hasSize(1);
            assertThat(resp.getDataPoints().get(0).getQuantity()).isEqualByComparingTo(BigDecimal.TEN);
            assertThat(resp.getDataPoints().get(0).getLabel()).isEqualTo("Jun 26");
        }

        @Test
        @DisplayName("null period defaults to daily")
        void nullPeriodDefaultsToDaily() {
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of());

            SalesGraphResponse resp = reportService.salesGraph(
                    null, LocalDate.now().minusDays(1), LocalDate.now(), null);

            assertThat(resp.getPeriod()).isEqualTo("daily");
        }

        @Test
        @DisplayName("spec order is anchored to DAILY_SPEC_ORDER across all bars")
        void specOrderIsConsistentAcrossBars() {
            LocalDateTime d1 = LocalDateTime.of(2026, 6, 15, 10, 0);
            LocalDateTime d2 = LocalDateTime.of(2026, 6, 16, 10, 0);
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(
                            makeTx(1L, john, vial5, 1, d1),   // only Vial 5ml on day1
                            makeTx(2L, jane, vial,  1, d2)    // only Vial 10ml on day2
                    ));

            SalesGraphResponse resp = reportService.salesGraph(
                    "daily", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 16), null);

            var day1 = resp.getDataPoints().get(0);
            var day2 = resp.getDataPoints().get(1);
            // Both bars must list specs in the same order
            var specs1 = day1.getSpecs().stream().map(SalesGraphResponse.SpecBreakdown::getSpecName).toList();
            var specs2 = day2.getSpecs().stream().map(SalesGraphResponse.SpecBreakdown::getSpecName).toList();
            assertThat(specs1).isEqualTo(specs2);
            // Vial 10 ml must come before Vial 5 ml (DAILY_SPEC_ORDER ordering)
            assertThat(specs1.indexOf("Vial 10 ml")).isLessThan(specs1.indexOf("Vial 5 ml"));
        }

        @Test
        @DisplayName("no filter (null) includes both regular and admin stock transactions")
        void noFilterIncludesBothStockTypes() {
            LocalDateTime at = LocalDateTime.of(2026, 6, 15, 10, 0);
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(
                            makeTx(1L, john, vial, 3, at, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK),
                            makeTx(2L, jane, vial, 2, at, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK)
                    ));

            SalesGraphResponse resp = reportService.salesGraph(
                    "daily", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15), null);

            assertThat(resp.getDataPoints().get(0).getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
        }

        @Test
        @DisplayName("\"ALL\" filter includes both regular and admin stock transactions, same as null")
        void allFilterIncludesBothStockTypes() {
            LocalDateTime at = LocalDateTime.of(2026, 6, 15, 10, 0);
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(
                            makeTx(1L, john, vial, 3, at, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK),
                            makeTx(2L, jane, vial, 2, at, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK)
                    ));

            SalesGraphResponse resp = reportService.salesGraph(
                    "daily", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15), "ALL");

            assertThat(resp.getDataPoints().get(0).getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
        }

        @Test
        @DisplayName("REGULAR_MEDICINE_STOCK filter excludes admin-stock transactions")
        void regularFilterExcludesAdminStock() {
            LocalDateTime at = LocalDateTime.of(2026, 6, 15, 10, 0);
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(
                            makeTx(1L, john, vial, 3, at, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK),
                            makeTx(2L, jane, vial, 2, at, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK)
                    ));

            SalesGraphResponse resp = reportService.salesGraph(
                    "daily", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15), "REGULAR_MEDICINE_STOCK");

            assertThat(resp.getDataPoints().get(0).getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(3));
        }

        @Test
        @DisplayName("ADMIN_MEDICINE_STOCK filter excludes regular-stock transactions")
        void adminFilterExcludesRegularStock() {
            LocalDateTime at = LocalDateTime.of(2026, 6, 15, 10, 0);
            when(transactionRepository.findApprovedBetween(any(), any(), any()))
                    .thenReturn(List.of(
                            makeTx(1L, john, vial, 3, at, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK),
                            makeTx(2L, jane, vial, 2, at, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK)
                    ));

            SalesGraphResponse resp = reportService.salesGraph(
                    "daily", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15), "ADMIN_MEDICINE_STOCK");

            assertThat(resp.getDataPoints().get(0).getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(2));
        }

        @Test
        @DisplayName("invalid medicineStockType value throws IllegalArgumentException (400 via GlobalExceptionHandler)")
        void invalidStockTypeThrows() {
            assertThatThrownBy(() -> reportService.salesGraph(
                    "daily", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15), "NOT_A_REAL_TYPE"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
