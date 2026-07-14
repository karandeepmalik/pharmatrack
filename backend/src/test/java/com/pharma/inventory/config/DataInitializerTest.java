package com.pharma.inventory.config;

import com.pharma.inventory.entity.Inventory;
import com.pharma.inventory.repository.InventoryAdjustmentRepository;
import com.pharma.inventory.repository.InventoryRepository;
import com.pharma.inventory.service.CurrentStockCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against the production bug where seeded inventory had no matching
 * InventoryAdjustment record — current stock is derived by forward-reconstructing
 * adjustment/transaction history (CurrentStockCalculator), so unaudited seeded stock was
 * invisible to it and showed as 0 available even though the Inventory row had a real quantity.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
@DisplayName("DataInitializer — genesis adjustment records")
class DataInitializerTest {

    @Autowired DataInitializer dataInitializer;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired InventoryAdjustmentRepository inventoryAdjustmentRepository;
    @Autowired CurrentStockCalculator currentStockCalculator;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reseed() {
        dataInitializer.reseed();
    }

    @Test
    @DisplayName("every seeded Inventory row has a matching genesis InventoryAdjustment")
    void everySeededRowHasGenesisAdjustment() {
        List<Inventory> allInventory = inventoryRepository.findAll();
        assertThat(allInventory).isNotEmpty();

        for (Inventory inv : allInventory) {
            List<com.pharma.inventory.entity.InventoryAdjustment> adjustments =
                    inventoryAdjustmentRepository.findAllUpToForUser(
                            inv.getUser().getId(), java.time.LocalDateTime.now().plusMinutes(1));
            boolean hasMatchingGenesis = adjustments.stream().anyMatch(a ->
                    a.getMedicine().getId().equals(inv.getMedicine().getId())
                            && a.getInventoryType() == inv.getInventoryType()
                            && a.getQuantity() == inv.getQuantity()
                            && "ADD".equals(a.getAdjustmentType()));
            assertThat(hasMatchingGenesis)
                    .as("Inventory row id=%d (user=%s, medicine=%d, qty=%d) has no matching genesis adjustment",
                            inv.getId(), inv.getUser().getUsername(), inv.getMedicine().getId(), inv.getQuantity())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("CurrentStockCalculator reconstructs the same quantity Inventory.quantity was seeded with")
    void reconstructedQuantityMatchesSeededQuantity() {
        List<Inventory> allInventory = inventoryRepository.findAll();

        for (Inventory inv : allInventory) {
            int settled = currentStockCalculator.settledQuantity(
                    inv.getUser().getId(), inv.getMedicine().getId(), inv.getInventoryType());
            assertThat(settled)
                    .as("Reconstructed quantity for user=%s medicine=%d should match seeded quantity",
                            inv.getUser().getUsername(), inv.getMedicine().getId())
                    .isEqualTo(inv.getQuantity());
        }
    }

    @Test
    @DisplayName("genesis adjustments are recorded as ADD, not in-transit")
    void genesisAdjustmentsAreSettledAdd() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_adjustments WHERE note IN ('Initial stock', 'Initial allocation') " +
                "AND (adjustment_type != 'ADD' OR in_transit = true)", Long.class);
        assertThat(count).isZero();
    }
}
