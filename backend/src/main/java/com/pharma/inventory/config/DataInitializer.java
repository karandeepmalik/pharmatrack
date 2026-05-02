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
    private final PasswordEncoder encoder;
    @Bean @Profile("!test")
    public CommandLineRunner seed(){
        return args->{
            if(users.count()>0){ log.info("Data already seeded, skipping."); return; }
            log.info("Seeding demo data...");
            User admin=users.save(User.builder().username("admin").email("admin@pharma.com").fullName("Admin User").password(encoder.encode("Admin@123")).role(User.Role.ADMIN).active(true).build());
            User john=users.save(User.builder().username("john.doe").email("john@pharma.com").fullName("John Doe").password(encoder.encode("User@123")).role(User.Role.USER).active(true).build());
            User jane=users.save(User.builder().username("jane.smith").email("jane@pharma.com").fullName("Jane Smith").password(encoder.encode("User@123")).role(User.Role.USER).active(true).build());
            PharmaCompany fip=pharmas.save(PharmaCompany.builder().name("FIP Shield").description("FIP treatment specialist").active(true).build());
            PharmaCompany medi=pharmas.save(PharmaCompany.builder().name("MediCure").description("General medicines").active(true).build());
            Medicine m1=medicines.save(Medicine.builder().name("FIP Shield Vial").type(Medicine.MedicineType.VIAL).specification(10.0).pharmaCompany(fip).active(true).build());
            Medicine m2=medicines.save(Medicine.builder().name("FIP Shield Tablet").type(Medicine.MedicineType.TABLET).specification(50.0).pharmaCompany(fip).active(true).build());
            Medicine m3=medicines.save(Medicine.builder().name("MediCure Capsule").type(Medicine.MedicineType.CAPSULE).specification(25.0).pharmaCompany(medi).active(true).build());
            inventory.save(Inventory.builder().user(john).medicine(m1).quantity(100).build());
            inventory.save(Inventory.builder().user(john).medicine(m2).quantity(200).build());
            inventory.save(Inventory.builder().user(jane).medicine(m1).quantity(50).build());
            inventory.save(Inventory.builder().user(jane).medicine(m3).quantity(150).build());
            log.info("Seeded: 3 users, 2 pharmas, 3 medicines, 4 inventory records.");
        };
    }
}
