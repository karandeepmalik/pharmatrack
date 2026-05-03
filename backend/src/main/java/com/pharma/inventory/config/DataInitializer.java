package com.pharma.inventory.config;
import com.pharma.inventory.entity.*;
import com.pharma.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration @RequiredArgsConstructor @Slf4j
public class DataInitializer {
    private final UserRepository users;
    private final PharmaCompanyRepository pharmas;
    private final MedicineRepository medicines;
    private final InventoryRepository inventory;
    private final TransactionRepository txRepo;
    private final PasswordEncoder encoder;

    @Bean @Profile("!test")
    public CommandLineRunner seed() {
        return args -> {
            if (medicines.existsByName("Shield FX Tablet 50 mg")) {
                log.info("Data already seeded with full Shield FX catalogue, skipping.");
                return;
            }
            reseed();
        };
    }

    public void reseed() {
        log.info("Clearing existing data and seeding fresh demo data with Shield FX medicines...");
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
        // System inventory holder — inactive (cannot log in)
        User lost = users.save(User.builder().username("lostinventory")
            .email("lostinventory@system.internal").fullName("System Inventory")
            .password(encoder.encode("NoLogin@System999!"))
            .role(User.Role.USER).active(false).build());

        // ── Pharma company ────────────────────────────────────────────
        PharmaCompany shieldFx = pharmas.save(PharmaCompany.builder().name("Shield FX")
            .description("Shield FX treatment specialist").active(true).build());

        // ── Medicines — VIAL: 5 mg/ml, 10 mg/ml | Tablet: 12 mg, 25 mg, 50 mg ──
        Medicine vial5  = medicines.save(Medicine.builder().name("Shield FX Vial 5 ml")
            .type(Medicine.MedicineType.VIAL).specification(5.0).pharmaCompany(shieldFx).active(true).build());
        Medicine vial10 = medicines.save(Medicine.builder().name("Shield FX Vial 10 ml")
            .type(Medicine.MedicineType.VIAL).specification(10.0).pharmaCompany(shieldFx).active(true).build());
        Medicine tab12  = medicines.save(Medicine.builder().name("Shield FX Tablet 12 mg")
            .type(Medicine.MedicineType.TABLET).specification(12.0).pharmaCompany(shieldFx).active(true).build());
        Medicine tab25  = medicines.save(Medicine.builder().name("Shield FX Tablet 25 mg")
            .type(Medicine.MedicineType.TABLET).specification(25.0).pharmaCompany(shieldFx).active(true).build());
        Medicine tab50  = medicines.save(Medicine.builder().name("Shield FX Tablet 50 mg")
            .type(Medicine.MedicineType.TABLET).specification(50.0).pharmaCompany(shieldFx).active(true).build());

        // ── System inventory — all medicines start at 0 ───────────────
        inventory.save(Inventory.builder().user(lost).medicine(vial5).quantity(0).build());
        inventory.save(Inventory.builder().user(lost).medicine(vial10).quantity(0).build());
        inventory.save(Inventory.builder().user(lost).medicine(tab12).quantity(0).build());
        inventory.save(Inventory.builder().user(lost).medicine(tab25).quantity(0).build());
        inventory.save(Inventory.builder().user(lost).medicine(tab50).quantity(0).build());

        log.info("Seeded: 4 users (incl. lostinventory), 1 pharma (Shield FX), 5 medicines, all inventory at 0.");
    }
}
