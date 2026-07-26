package com.pharma.medicinestock.config;

import com.pharma.medicinestock.entity.MedicineStock;
import com.pharma.medicinestock.repository.MedicineStockAdjustmentRepository;
import com.pharma.medicinestock.repository.MedicineStockRepository;
import com.pharma.medicinestock.service.CurrentStockCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against the production bug where seeded medicine stock had no matching
 * MedicineStockAdjustment record — current stock is derived by forward-reconstructing
 * adjustment/transaction history (CurrentStockCalculator), so unaudited seeded stock was
 * invisible to it and showed as 0 available even though the MedicineStock row had a real quantity.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
@DisplayName("DataInitializer — genesis adjustment records")
class DataInitializerTest {

    @Autowired DataInitializer dataInitializer;
    @Autowired MedicineStockRepository medicineStockRepository;
    @Autowired MedicineStockAdjustmentRepository medicineStockAdjustmentRepository;
    @Autowired CurrentStockCalculator currentStockCalculator;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reseed() {
        dataInitializer.reseed();
    }

    @Test
    @DisplayName("every seeded MedicineStock row has a matching genesis MedicineStockAdjustment")
    void everySeededRowHasGenesisAdjustment() {
        List<MedicineStock> allMedicineStock = medicineStockRepository.findAll();
        assertThat(allMedicineStock).isNotEmpty();

        for (MedicineStock inv : allMedicineStock) {
            List<com.pharma.medicinestock.entity.MedicineStockAdjustment> adjustments =
                    medicineStockAdjustmentRepository.findAllUpToForUser(
                            inv.getUser().getId(), java.time.LocalDateTime.now().plusMinutes(1));
            boolean hasMatchingGenesis = adjustments.stream().anyMatch(a ->
                    a.getMedicine().getId().equals(inv.getMedicine().getId())
                            && a.getMedicineStockType() == inv.getMedicineStockType()
                            && a.getQuantity().compareTo(inv.getQuantity()) == 0
                            && "ADD".equals(a.getAdjustmentType()));
            assertThat(hasMatchingGenesis)
                    .as("MedicineStock row id=%d (user=%s, medicine=%d, qty=%s) has no matching genesis adjustment",
                            inv.getId(), inv.getUser().getUsername(), inv.getMedicine().getId(), inv.getQuantity())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("CurrentStockCalculator reconstructs the same quantity MedicineStock.quantity was seeded with")
    void reconstructedQuantityMatchesSeededQuantity() {
        List<MedicineStock> allMedicineStock = medicineStockRepository.findAll();

        for (MedicineStock inv : allMedicineStock) {
            BigDecimal settled = currentStockCalculator.settledQuantity(
                    inv.getUser().getId(), inv.getMedicine().getId(), inv.getMedicineStockType());
            assertThat(settled)
                    .as("Reconstructed quantity for user=%s medicine=%d should match seeded quantity",
                            inv.getUser().getUsername(), inv.getMedicine().getId())
                    .isEqualByComparingTo(inv.getQuantity());
        }
    }

    @Test
    @DisplayName("genesis adjustments are recorded as ADD, not in-transit")
    void genesisAdjustmentsAreSettledAdd() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM medicine_stock_adjustments WHERE note IN ('Initial stock', 'Initial allocation') " +
                "AND (adjustment_type != 'ADD' OR in_transit = true)", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("seed() CommandLineRunner is gated to never run under the prod profile — reseed() wipes all data")
    void seedBeanIsGatedAgainstProdProfile() throws NoSuchMethodException {
        Profile profile = DataInitializer.class.getMethod("seed").getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("!test & !prod");
    }
}
