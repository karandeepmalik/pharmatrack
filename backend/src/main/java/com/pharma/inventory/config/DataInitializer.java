package com.pharma.inventory.config;
import com.pharma.inventory.entity.*;
import com.pharma.inventory.repository.*;
import com.pharma.inventory.util.QuantityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
@Configuration @RequiredArgsConstructor @Slf4j
public class DataInitializer {
    private final UserRepository users;
    private final PharmaCompanyRepository pharmas;
    private final MedicineRepository medicines;
    private final InventoryRepository inventory;
    private final TransactionRepository txRepo;
    private final InventoryAdjustmentRepository adjustments;
    private final PasswordEncoder encoder;

    @Bean @Profile("!test")
    public CommandLineRunner seed() {
        return args -> {
            // Reseed if medicines are missing OR if the admin account is gone
            // (guards against a partial reseed that cleared users but not medicines)
            if (medicines.existsByName("Shield FX Tablet 50 mg (10 Tablets)")
                    && users.existsByUsername("admin")) {
                log.info("Data already seeded — patching vial concentrations if missing.");
                patchVialConcentration();
                return;
            }
            reseed();
        };
    }

    private void patchVialConcentration() {
        medicines.findAll().stream()
            .filter(m -> m.getType() == Medicine.MedicineType.VIAL && m.getConcentrationMgPerMl() == null)
            .forEach(m -> { m.setConcentrationMgPerMl(20.0); medicines.save(m); });
    }

    public void reseed() {
        log.info("Clearing existing data and seeding fresh demo data with Shield FX medicines...");
        // Clear in FK-safe order: adjustments before medicines/users, screenshots cascade from transactions
        adjustments.deleteAll();
        txRepo.deleteAll();
        inventory.deleteAll();
        medicines.deleteAll();
        pharmas.deleteAll();
        users.deleteAll();

        // ── Users ─────────────────────────────────────────────────────
        users.save(User.builder().username("admin").email("admin@pharma.com")
            .fullName("Admin User").password(encoder.encode("Admin@123"))
            .role(User.Role.ADMIN).active(true).build());
        users.save(User.builder().username("john.doe").email("john@pharma.com")
            .fullName("John Doe").password(encoder.encode("User@123"))
            .role(User.Role.USER).active(true).build());
        users.save(User.builder().username("jane.smith").email("jane@pharma.com")
            .fullName("Jane Smith").password(encoder.encode("User@123"))
            .role(User.Role.USER).active(true).build());

        // ── Pharma company ────────────────────────────────────────────
        PharmaCompany shieldFx = pharmas.save(PharmaCompany.builder().name("Shield FX")
            .description("Shield FX treatment specialist").active(true).build());

        // ── Medicines — VIAL: 5 ml / 10 ml at 20 mg/ml | Tablet: 12, 25, 50 mg (10 Tablets) ──
        Medicine vial5  = medicines.save(Medicine.builder().name("Shield FX Vial 5 ml")
            .type(Medicine.MedicineType.VIAL).specification(5.0).concentrationMgPerMl(20.0).price(2000).pharmaCompany(shieldFx).active(true).build());
        Medicine vial10 = medicines.save(Medicine.builder().name("Shield FX Vial 10 ml")
            .type(Medicine.MedicineType.VIAL).specification(10.0).concentrationMgPerMl(20.0).price(4000).pharmaCompany(shieldFx).active(true).build());
        Medicine tab12  = medicines.save(Medicine.builder().name("Shield FX Tablet 12 mg (10 Tablets)")
            .type(Medicine.MedicineType.TABLET).specification(12.0).price(1750).pharmaCompany(shieldFx).active(true).build());
        Medicine tab25  = medicines.save(Medicine.builder().name("Shield FX Tablet 25 mg (10 Tablets)")
            .type(Medicine.MedicineType.TABLET).specification(25.0).price(4000).pharmaCompany(shieldFx).active(true).build());
        Medicine tab50  = medicines.save(Medicine.builder().name("Shield FX Tablet 50 mg (10 Tablets)")
            .type(Medicine.MedicineType.TABLET).specification(50.0).price(8000).pharmaCompany(shieldFx).active(true).build());

        // ── Seed initial admin inventory (system stock) ───────────────
        User adminUser = users.findByUsername("admin").orElseThrow();
        for (Medicine m : List.of(vial5, vial10, tab12, tab25, tab50)) {
            seedInventoryWithGenesisAdjustment(adminUser, m, 100,
                Inventory.InventoryType.ADMIN_MEDICINE_STOCK, "Initial stock");
        }

        // ── Seed initial user inventory (regular allocations) ─────────
        User john = users.findByUsername("john.doe").orElseThrow();
        User jane = users.findByUsername("jane.smith").orElseThrow();
        for (Medicine m : List.of(vial5, vial10, tab12, tab25, tab50)) {
            seedInventoryWithGenesisAdjustment(john, m, 20,
                Inventory.InventoryType.REGULAR_MEDICINE_STOCK, "Initial allocation");
            seedInventoryWithGenesisAdjustment(jane, m, 15,
                Inventory.InventoryType.REGULAR_MEDICINE_STOCK, "Initial allocation");
        }

        log.info("Seeded: 3 users, 1 pharma, 5 medicines, admin stock (100 each), user allocations (john=20, jane=15 each).");
    }

    /**
     * Seeds an Inventory row AND a matching genesis InventoryAdjustment.
     *
     * Current stock is derived by forward-reconstructing InventoryAdjustment + Transaction
     * history (see CurrentStockCalculator / ReportService), not by trusting Inventory.quantity
     * directly. Without a matching adjustment record, seeded stock is invisible to that
     * reconstruction — it would show 0 available until an admin happened to touch the bucket.
     * Recording the genesis adjustment here, once, at seed time keeps reconstruction complete
     * from the start instead of relying on a later backfill (which is exactly the mechanism
     * that caused production data drift previously — see git history on DataMigrationService).
     */
    private void seedInventoryWithGenesisAdjustment(User user, Medicine medicine, int quantity,
                                                      Inventory.InventoryType type, String note) {
        BigDecimal qty = QuantityUtil.round(BigDecimal.valueOf(quantity));
        inventory.save(Inventory.builder().user(user).medicine(medicine)
            .quantity(qty).inventoryType(type).lastNote(note).build());
        adjustments.save(InventoryAdjustment.builder()
            .user(user).medicine(medicine).quantity(qty).adjustmentType("ADD")
            .note(note).internalMovement(false).inTransit(false).wasInTransit(false)
            .transitDays(2).inventoryType(type).adjustedAt(LocalDateTime.now())
            .build());
    }
}
