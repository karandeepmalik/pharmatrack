package com.pharma.inventory.config;

import com.pharma.inventory.entity.Medicine;
import com.pharma.inventory.entity.Medicine.MedicineType;
import com.pharma.inventory.repository.InventoryRepository;
import com.pharma.inventory.repository.MedicineRepository;
import com.pharma.inventory.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Medicine Spec Validation — Shield FX catalogue")
class MedicineSpecValidationTest {

    @Autowired DataInitializer dataInitializer;
    @Autowired MedicineRepository medicineRepository;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void setup() {
        dataInitializer.reseed();
    }

    @Nested @DisplayName("Medicine catalogue")
    class Catalogue {

        @Test @DisplayName("Exactly 5 medicines are seeded")
        void exactlyFiveMedicines() {
            assertThat(medicineRepository.findAll()).hasSize(5);
        }

        @Test @DisplayName("All medicines belong to Shield FX pharma")
        void allMedicinesAreShieldFx() {
            medicineRepository.findAll().forEach(m ->
                assertThat(m.getPharmaCompany().getName())
                    .as("Pharma for %s", m.getName())
                    .isEqualTo("Shield FX")
            );
        }

        @Test @DisplayName("All medicine names start with 'Shield FX'")
        void allNamesStartWithShieldFx() {
            medicineRepository.findAll().forEach(m ->
                assertThat(m.getName())
                    .as("Medicine name should start with 'Shield FX'")
                    .startsWith("Shield FX")
            );
        }

        @Test @DisplayName("No FIP or MediCure medicines remain")
        void noLegacyMedicinesExist() {
            List<Medicine> all = medicineRepository.findAll();
            assertThat(all).noneMatch(m -> m.getName().contains("FIP"));
            assertThat(all).noneMatch(m -> m.getName().contains("MediCure"));
        }

        @Test @DisplayName("All 5 expected medicine names are present with updated tablet names")
        void expectedMedicineNamesPresent() {
            Set<String> names = Set.of(
                "Shield FX Vial 5 ml",
                "Shield FX Vial 10 ml",
                "Shield FX Tablet 12 mg (10 Tablets)",
                "Shield FX Tablet 25 mg (10 Tablets)",
                "Shield FX Tablet 50 mg (10 Tablets)"
            );
            List<String> actualNames = medicineRepository.findAll().stream()
                .map(Medicine::getName).toList();
            assertThat(actualNames).containsExactlyInAnyOrderElementsOf(names);
        }

        @Test @DisplayName("Old tablet names without (10 Tablets) do not exist")
        void oldTabletNamesAbsent() {
            List<Medicine> all = medicineRepository.findAll();
            assertThat(all).noneMatch(m -> m.getName().equals("Shield FX Tablet 12 mg"));
            assertThat(all).noneMatch(m -> m.getName().equals("Shield FX Tablet 25 mg"));
            assertThat(all).noneMatch(m -> m.getName().equals("Shield FX Tablet 50 mg"));
        }

        @Test @DisplayName("All medicines have a positive price")
        void allMedicinesHavePrice() {
            medicineRepository.findAll().forEach(m ->
                assertThat(m.getPrice())
                    .as("Price for %s", m.getName())
                    .isNotNull()
                    .isPositive()
            );
        }
    }

    @Nested @DisplayName("VIAL specifications")
    class VialSpecs {

        @Test @DisplayName("VIAL count is 2")
        void vialCount() {
            long count = medicineRepository.findAll().stream()
                .filter(m -> m.getType() == MedicineType.VIAL).count();
            assertThat(count).isEqualTo(2);
        }

        @Test @DisplayName("VIAL specification is 5 or 10 mg/ml")
        void vialSpecIs5Or10() {
            medicineRepository.findAll().stream()
                .filter(m -> m.getType() == MedicineType.VIAL)
                .forEach(m -> assertThat(m.getSpecification())
                    .as("VIAL spec for %s", m.getName())
                    .isIn(5.0, 10.0));
        }

        @Test @DisplayName("Shield FX Vial 5 ml has specification 5.0 and price 2000")
        void vial5mlHasSpec5() {
            Medicine vial = medicineRepository.findAll().stream()
                .filter(m -> m.getName().equals("Shield FX Vial 5 ml"))
                .findFirst().orElseThrow(() -> new AssertionError("Shield FX Vial 5 ml not found"));
            assertThat(vial.getSpecification()).isEqualTo(5.0);
            assertThat(vial.getType()).isEqualTo(MedicineType.VIAL);
            assertThat(vial.getPrice()).isEqualTo(2000);
        }

        @Test @DisplayName("Shield FX Vial 10 ml has specification 10.0 and price 4000")
        void vial10mlHasSpec10() {
            Medicine vial = medicineRepository.findAll().stream()
                .filter(m -> m.getName().equals("Shield FX Vial 10 ml"))
                .findFirst().orElseThrow(() -> new AssertionError("Shield FX Vial 10 ml not found"));
            assertThat(vial.getSpecification()).isEqualTo(10.0);
            assertThat(vial.getType()).isEqualTo(MedicineType.VIAL);
            assertThat(vial.getPrice()).isEqualTo(4000);
        }
    }

    @Nested @DisplayName("TABLET specifications")
    class TabletSpecs {

        @Test @DisplayName("TABLET count is 3")
        void tabletCount() {
            long count = medicineRepository.findAll().stream()
                .filter(m -> m.getType() == MedicineType.TABLET).count();
            assertThat(count).isEqualTo(3);
        }

        @Test @DisplayName("TABLET specification is 12, 25, or 50 mg")
        void tabletSpecIs12Or25Or50() {
            medicineRepository.findAll().stream()
                .filter(m -> m.getType() == MedicineType.TABLET)
                .forEach(m -> assertThat(m.getSpecification())
                    .as("TABLET spec for %s", m.getName())
                    .isIn(12.0, 25.0, 50.0));
        }

        @Test @DisplayName("Shield FX Tablet 12 mg (10 Tablets) has spec 12.0 and price 1750")
        void tablet12HasSpec12() {
            Medicine tab = medicineRepository.findAll().stream()
                .filter(m -> m.getName().equals("Shield FX Tablet 12 mg (10 Tablets)"))
                .findFirst().orElseThrow(() -> new AssertionError("Shield FX Tablet 12 mg (10 Tablets) not found"));
            assertThat(tab.getSpecification()).isEqualTo(12.0);
            assertThat(tab.getType()).isEqualTo(MedicineType.TABLET);
            assertThat(tab.getPrice()).isEqualTo(1750);
        }

        @Test @DisplayName("Shield FX Tablet 25 mg (10 Tablets) has spec 25.0 and price 4000")
        void tablet25HasSpec25() {
            Medicine tab = medicineRepository.findAll().stream()
                .filter(m -> m.getName().equals("Shield FX Tablet 25 mg (10 Tablets)"))
                .findFirst().orElseThrow(() -> new AssertionError("Shield FX Tablet 25 mg (10 Tablets) not found"));
            assertThat(tab.getSpecification()).isEqualTo(25.0);
            assertThat(tab.getType()).isEqualTo(MedicineType.TABLET);
            assertThat(tab.getPrice()).isEqualTo(4000);
        }

        @Test @DisplayName("Shield FX Tablet 50 mg (10 Tablets) has spec 50.0 and price 8000")
        void tablet50HasSpec50() {
            Medicine tab = medicineRepository.findAll().stream()
                .filter(m -> m.getName().equals("Shield FX Tablet 50 mg (10 Tablets)"))
                .findFirst().orElseThrow(() -> new AssertionError("Shield FX Tablet 50 mg (10 Tablets) not found"));
            assertThat(tab.getSpecification()).isEqualTo(50.0);
            assertThat(tab.getType()).isEqualTo(MedicineType.TABLET);
            assertThat(tab.getPrice()).isEqualTo(8000);
        }
    }

    @Nested @DisplayName("Seed state — no system user")
    class SeedState {

        @Test @DisplayName("No lostinventory system user exists after seed")
        void noSystemUserAfterSeed() {
            boolean exists = userRepository.findByUsername("lostinventory").isPresent();
            assertThat(exists).as("lostinventory system user should not exist").isFalse();
        }

        @Test @DisplayName("Exactly 3 active users are seeded")
        void exactlyThreeUsers() {
            long active = userRepository.findAll().stream().filter(u -> u.isActive()).count();
            assertThat(active).isEqualTo(3);
        }

        @Test @DisplayName("Seed creates 15 inventory records: 5 admin stock + 5 john.doe + 5 jane.smith")
        void inventorySeededCorrectly() {
            var all = inventoryRepository.findAll();
            assertThat(all).hasSize(15);
            long adminRows = all.stream()
                .filter(i -> i.getInventoryType() == com.pharma.inventory.entity.Inventory.InventoryType.ADMIN_MEDICINE_STOCK)
                .count();
            long userRows = all.stream()
                .filter(i -> i.getInventoryType() == com.pharma.inventory.entity.Inventory.InventoryType.REGULAR_MEDICINE_STOCK)
                .count();
            assertThat(adminRows).isEqualTo(5);
            assertThat(userRows).isEqualTo(10);
        }
    }
}
