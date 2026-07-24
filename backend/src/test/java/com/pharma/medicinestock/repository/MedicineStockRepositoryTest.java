package com.pharma.medicinestock.repository;

import com.pharma.medicinestock.entity.*;
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
@DisplayName("MedicineStockRepository JPQL integration")
class MedicineStockRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired MedicineStockRepository repo;

    private User user1;
    private User user2;
    private Medicine medA; // "Amoxicillin", spec=500.0
    private Medicine medB; // "Brufen",      spec=400.0

    // medicine stock rows
    private MedicineStock inv1; // user1, medA, REGULAR, qty=10
    private MedicineStock inv2; // user1, medB, REGULAR, qty=0   (zero — excluded from non-zero queries)
    private MedicineStock inv3; // user2, medA, REGULAR, qty=5
    private MedicineStock inv4; // user1, medA, ADMIN,   qty=8
    private MedicineStock inv5; // user2, medB, ADMIN,   qty=3

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

        inv1 = buildMedicineStock(user1, medA, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 10);
        inv2 = buildMedicineStock(user1, medB, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 0);
        inv3 = buildMedicineStock(user2, medA, MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK, 5);
        inv4 = buildMedicineStock(user1, medA, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK, 8);
        inv5 = buildMedicineStock(user2, medB, MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK, 3);
        em.persist(inv1);
        em.persist(inv2);
        em.persist(inv3);
        em.persist(inv4);
        em.persist(inv5);
        em.flush();
    }

    // ── derived queries ──────────────────────────────────────────────────────

    @Nested @DisplayName("findByUserIdAndMedicineIdAndMedicineStockType")
    class FindByUserIdAndMedicineIdAndMedicineStockType {

        @Test @DisplayName("returns exact match for REGULAR type")
        void returnsRegularMatch() {
            Optional<MedicineStock> result = repo.findByUserIdAndMedicineIdAndMedicineStockType(
                    user1.getId(), medA.getId(), MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(inv1.getId());
        }

        @Test @DisplayName("returns exact match for ADMIN type")
        void returnsAdminMatch() {
            Optional<MedicineStock> result = repo.findByUserIdAndMedicineIdAndMedicineStockType(
                    user1.getId(), medA.getId(), MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK);
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(inv4.getId());
        }

        @Test @DisplayName("returns empty when type does not match")
        void returnsEmptyOnTypeMismatch() {
            // user2 has no ADMIN medicine stock for medA
            Optional<MedicineStock> result = repo.findByUserIdAndMedicineIdAndMedicineStockType(
                    user2.getId(), medA.getId(), MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK);
            assertThat(result).isEmpty();
        }
    }

    // ── @Query methods ───────────────────────────────────────────────────────

    @Nested @DisplayName("findAvailableByUserIdAndType")
    class FindAvailableByUserIdAndType {

        @Test @DisplayName("returns only non-zero REGULAR rows for the user")
        void returnsNonZeroRegularForUser() {
            List<MedicineStock> result = repo.findAvailableByUserIdAndType(
                    user1.getId(), MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
            assertThat(result).extracting(MedicineStock::getId).containsExactly(inv1.getId());
        }

        @Test @DisplayName("excludes zero-quantity rows")
        void excludesZeroQty() {
            List<MedicineStock> result = repo.findAvailableByUserIdAndType(
                    user1.getId(), MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
            assertThat(result).noneMatch(i -> i.getId().equals(inv2.getId())); // inv2 qty=0
        }

        @Test @DisplayName("excludes rows belonging to other users")
        void excludesOtherUsers() {
            List<MedicineStock> result = repo.findAvailableByUserIdAndType(
                    user1.getId(), MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
            assertThat(result).noneMatch(i -> i.getUser().getId().equals(user2.getId()));
        }

        @Test @DisplayName("excludes rows of wrong medicineStock type")
        void excludesWrongType() {
            List<MedicineStock> result = repo.findAvailableByUserIdAndType(
                    user1.getId(), MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
            assertThat(result).noneMatch(
                    i -> i.getMedicineStockType() == MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK);
        }

        @Test @DisplayName("eagerly loads medicine and pharmaCompany (no LazyInitializationException)")
        void eagerlyLoadsMedicineAndCompany() {
            List<MedicineStock> result = repo.findAvailableByUserIdAndType(
                    user1.getId(), MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
            assertThat(result.get(0).getMedicine().getName()).isEqualTo("Amoxicillin");
            assertThat(result.get(0).getMedicine().getPharmaCompany().getName()).isEqualTo("TestPharma");
        }

        @Test @DisplayName("returns empty when user has no medicineStock of that type")
        void returnsEmptyWhenNone() {
            User user3 = buildUser("carol", "carol@test.com");
            em.persist(user3);
            em.flush();
            List<MedicineStock> result = repo.findAvailableByUserIdAndType(
                    user3.getId(), MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
            assertThat(result).isEmpty();
        }
    }

    @Nested @DisplayName("findAllNonZeroOrderByMedicineAndUser")
    class FindAllNonZeroOrderByMedicineAndUser {

        @Test @DisplayName("returns only non-zero REGULAR rows")
        void returnsNonZeroRegularRows() {
            List<MedicineStock> result = repo.findAllNonZeroOrderByMedicineAndUser(
                    MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
            assertThat(result).extracting(MedicineStock::getId)
                    .containsExactlyInAnyOrder(inv1.getId(), inv3.getId());
        }

        @Test @DisplayName("excludes ADMIN rows")
        void excludesAdminRows() {
            List<MedicineStock> result = repo.findAllNonZeroOrderByMedicineAndUser(
                    MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
            assertThat(result).noneMatch(
                    i -> i.getMedicineStockType() == MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK);
        }

        @Test @DisplayName("ordered by medicine name then user fullName (alice before bob)")
        void orderedByMedicineThenUser() {
            List<MedicineStock> result = repo.findAllNonZeroOrderByMedicineAndUser(
                    MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
            // Both are medA (Amoxicillin): alice (user1) before bob (user2)
            assertThat(result).extracting(MedicineStock::getId)
                    .containsExactly(inv1.getId(), inv3.getId());
        }

        @Test @DisplayName("returns ADMIN rows when ADMIN type requested")
        void returnsAdminRowsWhenAdminType() {
            List<MedicineStock> result = repo.findAllNonZeroOrderByMedicineAndUser(
                    MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK);
            assertThat(result).extracting(MedicineStock::getId)
                    .containsExactlyInAnyOrder(inv4.getId(), inv5.getId());
        }

        @Test @DisplayName("eagerly loads user, medicine, pharmaCompany")
        void eagerlyLoadsAssociations() {
            List<MedicineStock> result = repo.findAllNonZeroOrderByMedicineAndUser(
                    MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
            assertThat(result.get(0).getUser().getUsername()).isEqualTo("alice");
            assertThat(result.get(0).getMedicine().getName()).isEqualTo("Amoxicillin");
            assertThat(result.get(0).getMedicine().getPharmaCompany().getName()).isEqualTo("TestPharma");
        }
    }

    @Nested @DisplayName("findAllNonZeroRegularOrderByMedicineAndUser")
    class FindAllNonZeroRegularOrderByMedicineAndUser {

        @Test @DisplayName("returns non-zero REGULAR rows (same as exact-match when no NULL rows in H2)")
        void returnsNonZeroRegularRows() {
            List<MedicineStock> result = repo.findAllNonZeroRegularOrderByMedicineAndUser(
                    MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
            assertThat(result).extracting(MedicineStock::getId)
                    .containsExactlyInAnyOrder(inv1.getId(), inv3.getId());
        }

        @Test @DisplayName("excludes ADMIN rows")
        void excludesAdminRows() {
            List<MedicineStock> result = repo.findAllNonZeroRegularOrderByMedicineAndUser(
                    MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
            assertThat(result).noneMatch(
                    i -> i.getMedicineStockType() == MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK);
        }

        @Test @DisplayName("excludes zero-quantity rows")
        void excludesZeroQty() {
            List<MedicineStock> result = repo.findAllNonZeroRegularOrderByMedicineAndUser(
                    MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
            assertThat(result).noneMatch(i -> i.getId().equals(inv2.getId()));
        }
    }

    @Nested @DisplayName("findAllNonZeroRegularForValuation")
    class FindAllNonZeroRegularForValuation {

        @Test @DisplayName("returns non-zero REGULAR rows (includes IS NULL fallback in query)")
        void returnsNonZeroRegularRows() {
            List<MedicineStock> result = repo.findAllNonZeroRegularForValuation(
                    MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
            assertThat(result).extracting(MedicineStock::getId)
                    .containsExactlyInAnyOrder(inv1.getId(), inv3.getId());
        }

        @Test @DisplayName("excludes ADMIN rows")
        void excludesAdminRows() {
            List<MedicineStock> result = repo.findAllNonZeroRegularForValuation(
                    MedicineStock.MedicineStockType.REGULAR_MEDICINE_STOCK);
            assertThat(result).noneMatch(
                    i -> i.getMedicineStockType() == MedicineStock.MedicineStockType.ADMIN_MEDICINE_STOCK);
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

    private MedicineStock buildMedicineStock(User user, Medicine medicine,
                                      MedicineStock.MedicineStockType type, int qty) {
        return MedicineStock.builder()
                .user(user)
                .medicine(medicine)
                .medicineStockType(type)
                .quantity(BigDecimal.valueOf(qty))
                .lastUpdated(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();
    }
}
