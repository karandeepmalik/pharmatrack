package com.pharma.inventory.repository;

import com.pharma.inventory.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("InventoryRepository JPQL integration")
class InventoryRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired InventoryRepository repo;

    private User user1;
    private User user2;
    private Medicine medA; // "Amoxicillin", spec=500.0
    private Medicine medB; // "Brufen",      spec=400.0

    // inventory rows
    private Inventory inv1; // user1, medA, REGULAR, qty=10
    private Inventory inv2; // user1, medB, REGULAR, qty=0   (zero — excluded from non-zero queries)
    private Inventory inv3; // user2, medA, REGULAR, qty=5
    private Inventory inv4; // user1, medA, ADMIN,   qty=8
    private Inventory inv5; // user2, medB, ADMIN,   qty=3

    @BeforeEach
    void setUp() {
        PharmaCompany company = new PharmaCompany();
        company.setName("TestPharma");
        em.persist(company);

        medA = Medicine.builder().name("Amoxicillin").type(Medicine.MedicineType.TABLET)
                .specification(500.0).price(20).pharmaCompany(company).build();
        medB = Medicine.builder().name("Brufen").type(Medicine.MedicineType.TABLET)
                .specification(400.0).price(15).pharmaCompany(company).build();
        em.persist(medA);
        em.persist(medB);

        user1 = buildUser("alice", "alice@test.com");
        user2 = buildUser("bob",   "bob@test.com");
        em.persist(user1);
        em.persist(user2);

        inv1 = buildInventory(user1, medA, Inventory.InventoryType.REGULAR_MEDICINE_STOCK, 10);
        inv2 = buildInventory(user1, medB, Inventory.InventoryType.REGULAR_MEDICINE_STOCK, 0);
        inv3 = buildInventory(user2, medA, Inventory.InventoryType.REGULAR_MEDICINE_STOCK, 5);
        inv4 = buildInventory(user1, medA, Inventory.InventoryType.ADMIN_MEDICINE_STOCK, 8);
        inv5 = buildInventory(user2, medB, Inventory.InventoryType.ADMIN_MEDICINE_STOCK, 3);
        em.persist(inv1);
        em.persist(inv2);
        em.persist(inv3);
        em.persist(inv4);
        em.persist(inv5);
        em.flush();
    }

    // ── derived queries ──────────────────────────────────────────────────────

    @Nested @DisplayName("findByUserIdAndMedicineIdAndInventoryType")
    class FindByUserIdAndMedicineIdAndInventoryType {

        @Test @DisplayName("returns exact match for REGULAR type")
        void returnsRegularMatch() {
            Optional<Inventory> result = repo.findByUserIdAndMedicineIdAndInventoryType(
                    user1.getId(), medA.getId(), Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(inv1.getId());
        }

        @Test @DisplayName("returns exact match for ADMIN type")
        void returnsAdminMatch() {
            Optional<Inventory> result = repo.findByUserIdAndMedicineIdAndInventoryType(
                    user1.getId(), medA.getId(), Inventory.InventoryType.ADMIN_MEDICINE_STOCK);
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(inv4.getId());
        }

        @Test @DisplayName("returns empty when type does not match")
        void returnsEmptyOnTypeMismatch() {
            // user2 has no ADMIN inventory for medA
            Optional<Inventory> result = repo.findByUserIdAndMedicineIdAndInventoryType(
                    user2.getId(), medA.getId(), Inventory.InventoryType.ADMIN_MEDICINE_STOCK);
            assertThat(result).isEmpty();
        }
    }

    // ── @Query methods ───────────────────────────────────────────────────────

    @Nested @DisplayName("findAvailableByUserIdAndType")
    class FindAvailableByUserIdAndType {

        @Test @DisplayName("returns only non-zero REGULAR rows for the user")
        void returnsNonZeroRegularForUser() {
            List<Inventory> result = repo.findAvailableByUserIdAndType(
                    user1.getId(), Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result).extracting(Inventory::getId).containsExactly(inv1.getId());
        }

        @Test @DisplayName("excludes zero-quantity rows")
        void excludesZeroQty() {
            List<Inventory> result = repo.findAvailableByUserIdAndType(
                    user1.getId(), Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result).noneMatch(i -> i.getId().equals(inv2.getId())); // inv2 qty=0
        }

        @Test @DisplayName("excludes rows belonging to other users")
        void excludesOtherUsers() {
            List<Inventory> result = repo.findAvailableByUserIdAndType(
                    user1.getId(), Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result).noneMatch(i -> i.getUser().getId().equals(user2.getId()));
        }

        @Test @DisplayName("excludes rows of wrong inventory type")
        void excludesWrongType() {
            List<Inventory> result = repo.findAvailableByUserIdAndType(
                    user1.getId(), Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result).noneMatch(
                    i -> i.getInventoryType() == Inventory.InventoryType.ADMIN_MEDICINE_STOCK);
        }

        @Test @DisplayName("eagerly loads medicine and pharmaCompany (no LazyInitializationException)")
        void eagerlyLoadsMedicineAndCompany() {
            List<Inventory> result = repo.findAvailableByUserIdAndType(
                    user1.getId(), Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result.get(0).getMedicine().getName()).isEqualTo("Amoxicillin");
            assertThat(result.get(0).getMedicine().getPharmaCompany().getName()).isEqualTo("TestPharma");
        }

        @Test @DisplayName("returns empty when user has no inventory of that type")
        void returnsEmptyWhenNone() {
            User user3 = buildUser("carol", "carol@test.com");
            em.persist(user3);
            em.flush();
            List<Inventory> result = repo.findAvailableByUserIdAndType(
                    user3.getId(), Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result).isEmpty();
        }
    }

    @Nested @DisplayName("findAllNonZeroOrderByMedicineAndUser")
    class FindAllNonZeroOrderByMedicineAndUser {

        @Test @DisplayName("returns only non-zero REGULAR rows")
        void returnsNonZeroRegularRows() {
            List<Inventory> result = repo.findAllNonZeroOrderByMedicineAndUser(
                    Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result).extracting(Inventory::getId)
                    .containsExactlyInAnyOrder(inv1.getId(), inv3.getId());
        }

        @Test @DisplayName("excludes ADMIN rows")
        void excludesAdminRows() {
            List<Inventory> result = repo.findAllNonZeroOrderByMedicineAndUser(
                    Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result).noneMatch(
                    i -> i.getInventoryType() == Inventory.InventoryType.ADMIN_MEDICINE_STOCK);
        }

        @Test @DisplayName("ordered by medicine name then user fullName (alice before bob)")
        void orderedByMedicineThenUser() {
            List<Inventory> result = repo.findAllNonZeroOrderByMedicineAndUser(
                    Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            // Both are medA (Amoxicillin): alice (user1) before bob (user2)
            assertThat(result).extracting(Inventory::getId)
                    .containsExactly(inv1.getId(), inv3.getId());
        }

        @Test @DisplayName("returns ADMIN rows when ADMIN type requested")
        void returnsAdminRowsWhenAdminType() {
            List<Inventory> result = repo.findAllNonZeroOrderByMedicineAndUser(
                    Inventory.InventoryType.ADMIN_MEDICINE_STOCK);
            assertThat(result).extracting(Inventory::getId)
                    .containsExactlyInAnyOrder(inv4.getId(), inv5.getId());
        }

        @Test @DisplayName("eagerly loads user, medicine, pharmaCompany")
        void eagerlyLoadsAssociations() {
            List<Inventory> result = repo.findAllNonZeroOrderByMedicineAndUser(
                    Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result.get(0).getUser().getUsername()).isEqualTo("alice");
            assertThat(result.get(0).getMedicine().getName()).isEqualTo("Amoxicillin");
            assertThat(result.get(0).getMedicine().getPharmaCompany().getName()).isEqualTo("TestPharma");
        }
    }

    @Nested @DisplayName("findAllNonZeroRegularOrderByMedicineAndUser")
    class FindAllNonZeroRegularOrderByMedicineAndUser {

        @Test @DisplayName("returns non-zero REGULAR rows (same as exact-match when no NULL rows in H2)")
        void returnsNonZeroRegularRows() {
            List<Inventory> result = repo.findAllNonZeroRegularOrderByMedicineAndUser(
                    Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result).extracting(Inventory::getId)
                    .containsExactlyInAnyOrder(inv1.getId(), inv3.getId());
        }

        @Test @DisplayName("excludes ADMIN rows")
        void excludesAdminRows() {
            List<Inventory> result = repo.findAllNonZeroRegularOrderByMedicineAndUser(
                    Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result).noneMatch(
                    i -> i.getInventoryType() == Inventory.InventoryType.ADMIN_MEDICINE_STOCK);
        }

        @Test @DisplayName("excludes zero-quantity rows")
        void excludesZeroQty() {
            List<Inventory> result = repo.findAllNonZeroRegularOrderByMedicineAndUser(
                    Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result).noneMatch(i -> i.getId().equals(inv2.getId()));
        }
    }

    @Nested @DisplayName("findAllNonZeroRegularForValuation")
    class FindAllNonZeroRegularForValuation {

        @Test @DisplayName("returns non-zero REGULAR rows (includes IS NULL fallback in query)")
        void returnsNonZeroRegularRows() {
            List<Inventory> result = repo.findAllNonZeroRegularForValuation(
                    Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result).extracting(Inventory::getId)
                    .containsExactlyInAnyOrder(inv1.getId(), inv3.getId());
        }

        @Test @DisplayName("excludes ADMIN rows")
        void excludesAdminRows() {
            List<Inventory> result = repo.findAllNonZeroRegularForValuation(
                    Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result).noneMatch(
                    i -> i.getInventoryType() == Inventory.InventoryType.ADMIN_MEDICINE_STOCK);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User buildUser(String username, String email) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("secret");
        u.setFullName(username);
        u.setEmail(email);
        u.setRole(User.Role.USER);
        return u;
    }

    private Inventory buildInventory(User user, Medicine medicine,
                                      Inventory.InventoryType type, int qty) {
        return Inventory.builder()
                .user(user)
                .medicine(medicine)
                .inventoryType(type)
                .quantity(BigDecimal.valueOf(qty))
                .lastUpdated(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();
    }
}
