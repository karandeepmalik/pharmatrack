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
            if (users.existsByUsername("lostinventory")) {
                log.info("Data already seeded (lostinventory exists), skipping.");
                return;
            }
            reseed();
        };
    }

    public void reseed() {
        log.info("Clearing existing data and seeding fresh demo data...");
        txRepo.deleteAll();
        inventory.deleteAll();
        medicines.deleteAll();
        pharmas.deleteAll();
        users.deleteAll();

        // ── Users ─────────────────────────────────────────────────────
        users.save(User.builder().username("admin").email("admin@pharma.com")
            .fullName("Admin User").password(encoder.encode("Admin@123"))
            .role(User.Role.ADMIN).active(true).build());
        User john = users.save(User.builder().username("john.doe").email("john@pharma.com")
            .fullName("John Doe").password(encoder.encode("User@123"))
            .role(User.Role.USER).active(true).build());
        User jane = users.save(User.builder().username("jane.smith").email("jane@pharma.com")
            .fullName("Jane Smith").password(encoder.encode("User@123"))
            .role(User.Role.USER).active(true).build());
        // System inventory holder — inactive (cannot log in)
        User lost = users.save(User.builder().username("lostinventory")
            .email("lostinventory@system.internal").fullName("System Inventory")
            .password(encoder.encode("NoLogin@System999!"))
            .role(User.Role.USER).active(false).build());

        // ── Pharma companies ──────────────────────────────────────────
        PharmaCompany fip  = pharmas.save(PharmaCompany.builder().name("FIP Shield")
            .description("FIP treatment specialist").active(true).build());
        PharmaCompany medi = pharmas.save(PharmaCompany.builder().name("MediCure")
            .description("General medicines").active(true).build());

        // ── Medicines — VIAL: 5 mg/ml, 10 mg/ml | Tablet: 12mg, 25mg, 50mg ──
        Medicine vial5  = medicines.save(Medicine.builder().name("FIP Vial 5mg/ml")
            .type(Medicine.MedicineType.VIAL).specification(5.0).pharmaCompany(fip).active(true).build());
        Medicine vial10 = medicines.save(Medicine.builder().name("FIP Vial 10mg/ml")
            .type(Medicine.MedicineType.VIAL).specification(10.0).pharmaCompany(fip).active(true).build());
        Medicine tab12  = medicines.save(Medicine.builder().name("FIP Tablet 12mg")
            .type(Medicine.MedicineType.TABLET).specification(12.0).pharmaCompany(fip).active(true).build());
        Medicine tab50  = medicines.save(Medicine.builder().name("FIP Tablet 50mg")
            .type(Medicine.MedicineType.TABLET).specification(50.0).pharmaCompany(fip).active(true).build());
        Medicine tab25  = medicines.save(Medicine.builder().name("MediCure Tablet 25mg")
            .type(Medicine.MedicineType.TABLET).specification(25.0).pharmaCompany(medi).active(true).build());

        // ── System inventory (lostinventory holds total stock) ────────
        inventory.save(Inventory.builder().user(lost).medicine(vial5).quantity(500).build());
        inventory.save(Inventory.builder().user(lost).medicine(vial10).quantity(400).build());
        inventory.save(Inventory.builder().user(lost).medicine(tab12).quantity(300).build());
        inventory.save(Inventory.builder().user(lost).medicine(tab50).quantity(600).build());
        inventory.save(Inventory.builder().user(lost).medicine(tab25).quantity(350).build());

        // ── Allocate to users (deducts from system) ───────────────────
        allocate(lost, john, vial5,  100);
        allocate(lost, john, tab50,  200);
        allocate(lost, jane, vial10,  50);
        allocate(lost, jane, tab25,  150);

        log.info("Seeded: 4 users (incl. lostinventory), 2 pharmas, 5 medicines, system + user inventory.");
    }

    private void allocate(User system, User user, Medicine medicine, int qty) {
        Inventory sysInv = inventory.findByUserIdAndMedicineId(system.getId(), medicine.getId()).orElseThrow();
        sysInv.setQuantity(sysInv.getQuantity() - qty);
        inventory.save(sysInv);
        inventory.save(Inventory.builder().user(user).medicine(medicine).quantity(qty).build());
    }
}
