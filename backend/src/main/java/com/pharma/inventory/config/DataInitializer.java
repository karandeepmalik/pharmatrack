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
            if (medicines.existsByName("Shield FX Tablet 50 mg (10 Tablets)")) {
                log.info("Data already seeded with full Shield FX catalogue (10 Tablets), skipping.");
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

        // ── Pharma company ────────────────────────────────────────────
        PharmaCompany shieldFx = pharmas.save(PharmaCompany.builder().name("Shield FX")
            .description("Shield FX treatment specialist").active(true).build());

        // ── Medicines — VIAL: 5 mg/ml, 10 mg/ml | Tablet: 12, 25, 50 mg (10 Tablets) ──
        medicines.save(Medicine.builder().name("Shield FX Vial 5 ml")
            .type(Medicine.MedicineType.VIAL).specification(5.0).price(2000).pharmaCompany(shieldFx).active(true).build());
        medicines.save(Medicine.builder().name("Shield FX Vial 10 ml")
            .type(Medicine.MedicineType.VIAL).specification(10.0).price(4000).pharmaCompany(shieldFx).active(true).build());
        medicines.save(Medicine.builder().name("Shield FX Tablet 12 mg (10 Tablets)")
            .type(Medicine.MedicineType.TABLET).specification(12.0).price(1750).pharmaCompany(shieldFx).active(true).build());
        medicines.save(Medicine.builder().name("Shield FX Tablet 25 mg (10 Tablets)")
            .type(Medicine.MedicineType.TABLET).specification(25.0).price(4000).pharmaCompany(shieldFx).active(true).build());
        medicines.save(Medicine.builder().name("Shield FX Tablet 50 mg (10 Tablets)")
            .type(Medicine.MedicineType.TABLET).specification(50.0).price(8000).pharmaCompany(shieldFx).active(true).build());

        log.info("Seeded: 3 users (admin, john.doe, jane.smith), 1 pharma (Shield FX), 5 medicines with prices.");
    }
}
