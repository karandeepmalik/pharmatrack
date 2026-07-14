package com.pharma.inventory.repository;

import com.pharma.inventory.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("InventoryAdjustmentRepository JPQL integration")
class InventoryAdjustmentRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired InventoryAdjustmentRepository repo;

    private User user1;
    private User admin;
    private Medicine medicine;

    private InventoryAdjustment adj1; // Jan 1, inTransit=false, adjustedBy=null
    private InventoryAdjustment adj2; // Jan 2, inTransit=true,  adjustedBy=null
    private InventoryAdjustment adj3; // Jan 3, inTransit=false, adjustedBy=admin

    private static final LocalDateTime JAN_1 = LocalDateTime.of(2024, 1, 1, 10, 0);
    private static final LocalDateTime JAN_2 = LocalDateTime.of(2024, 1, 2, 10, 0);
    private static final LocalDateTime JAN_3 = LocalDateTime.of(2024, 1, 3, 10, 0);

    @BeforeEach
    void setUp() {
        PharmaCompany company = new PharmaCompany();
        company.setName("TestPharma");
        em.persist(company);

        medicine = Medicine.builder()
                .name("Paracetamol").type(Medicine.MedicineType.TABLET)
                .specification(500.0).price(10).pharmaCompany(company).build();
        em.persist(medicine);

        user1 = buildUser("alice", "alice@test.com", User.Role.USER);
        admin = buildUser("admin", "admin@test.com", User.Role.ADMIN);
        em.persist(user1);
        em.persist(admin);

        adj1 = buildAdj(user1, JAN_1, false, null);
        adj2 = buildAdj(user1, JAN_2, true,  null);
        adj3 = buildAdj(user1, JAN_3, false, admin);
        em.persist(adj1);
        em.persist(adj2);
        em.persist(adj3);
        em.flush();
    }

    // ── findByDateRange ──────────────────────────────────────────────────────

    @Nested @DisplayName("findByDateRange")
    class FindByDateRange {

        @Test @DisplayName("returns adjustments within [start, end) range")
        void returnsWithinRange() {
            // [Jan 1 00:00, Jan 3 00:00) → adj1 (10:00) and adj2 (10:00) included; adj3 (Jan 3 10:00) excluded
            List<InventoryAdjustment> result = repo.findByDateRange(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 3, 0, 0));
            assertThat(result).extracting(InventoryAdjustment::getId)
                    .containsExactlyInAnyOrder(adj1.getId(), adj2.getId());
        }

        @Test @DisplayName("excludes adjustment exactly at end boundary (exclusive)")
        void excludesExactEndBoundary() {
            List<InventoryAdjustment> result = repo.findByDateRange(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    JAN_3); // end = exactly adj3.adjustedAt
            assertThat(result).noneMatch(a -> a.getId().equals(adj3.getId()));
        }

        @Test @DisplayName("returns empty when range matches no adjustments")
        void emptyWhenNoMatch() {
            List<InventoryAdjustment> result = repo.findByDateRange(
                    LocalDateTime.of(2025, 1, 1, 0, 0),
                    LocalDateTime.of(2025, 1, 2, 0, 0));
            assertThat(result).isEmpty();
        }

        @Test @DisplayName("ordered by adjustedAt ASC")
        void orderedAscending() {
            List<InventoryAdjustment> result = repo.findByDateRange(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0));
            assertThat(result).extracting(InventoryAdjustment::getId)
                    .containsExactly(adj1.getId(), adj2.getId(), adj3.getId());
        }

        @Test @DisplayName("eagerly loads user, medicine, pharmaCompany")
        void eagerlyLoadsAssociations() {
            List<InventoryAdjustment> result = repo.findByDateRange(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0));
            assertThat(result.get(0).getUser().getUsername()).isEqualTo("alice");
            assertThat(result.get(0).getMedicine().getName()).isEqualTo("Paracetamol");
            assertThat(result.get(0).getMedicine().getPharmaCompany().getName()).isEqualTo("TestPharma");
        }
    }

    // ── findAllActiveInTransit ───────────────────────────────────────────────

    @Nested @DisplayName("findAllActiveInTransit")
    class FindAllActiveInTransit {

        @Test @DisplayName("returns only inTransit=true records")
        void returnsOnlyInTransit() {
            List<InventoryAdjustment> result = repo.findAllActiveInTransit();
            assertThat(result).extracting(InventoryAdjustment::getId).containsExactly(adj2.getId());
        }

        @Test @DisplayName("excludes inTransit=false records")
        void excludesNotInTransit() {
            List<InventoryAdjustment> result = repo.findAllActiveInTransit();
            assertThat(result).noneMatch(a -> a.getId().equals(adj1.getId()));
            assertThat(result).noneMatch(a -> a.getId().equals(adj3.getId()));
        }

        @Test @DisplayName("returns empty when no in-transit records exist")
        void emptyWhenNone() {
            adj2.setInTransit(false);
            em.merge(adj2);
            em.flush();
            assertThat(repo.findAllActiveInTransit()).isEmpty();
        }

        @Test @DisplayName("eagerly loads user and medicine")
        void eagerlyLoadsAssociations() {
            List<InventoryAdjustment> result = repo.findAllActiveInTransit();
            assertThat(result.get(0).getUser().getUsername()).isEqualTo("alice");
            assertThat(result.get(0).getMedicine().getName()).isEqualTo("Paracetamol");
        }
    }

    // ── findActiveInTransitFor ───────────────────────────────────────────────

    @Nested @DisplayName("findActiveInTransitFor")
    class FindActiveInTransitFor {

        @BeforeEach
        void makeAdj2AnAddType() {
            // Fixture default is adjustmentType="DISPENSED"; this method only matches ADD.
            adj2.setAdjustmentType("ADD");
            em.merge(adj2);
            em.flush();
        }

        @Test @DisplayName("returns in-transit ADD adjustment matching user/medicine/type")
        void returnsMatchingInTransitAdd() {
            List<InventoryAdjustment> result =
                    repo.findActiveInTransitFor(user1.getId(), medicine.getId(), Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result).extracting(InventoryAdjustment::getId).containsExactly(adj2.getId());
        }

        @Test @DisplayName("excludes records with inTransit=false")
        void excludesNotInTransit() {
            List<InventoryAdjustment> result =
                    repo.findActiveInTransitFor(user1.getId(), medicine.getId(), Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result).noneMatch(a -> a.getId().equals(adj1.getId()) || a.getId().equals(adj3.getId()));
        }

        @Test @DisplayName("excludes records for a different user")
        void excludesDifferentUser() {
            List<InventoryAdjustment> result =
                    repo.findActiveInTransitFor(admin.getId(), medicine.getId(), Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result).isEmpty();
        }

        @Test @DisplayName("excludes non-ADD adjustment types even if inTransit=true")
        void excludesNonAddType() {
            adj2.setAdjustmentType("REDUCE");
            em.merge(adj2);
            em.flush();

            List<InventoryAdjustment> result =
                    repo.findActiveInTransitFor(user1.getId(), medicine.getId(), Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result).isEmpty();
        }

        @Test @DisplayName("returns empty when no in-transit adjustments exist for the bucket")
        void emptyWhenNoneInTransit() {
            adj2.setInTransit(false);
            em.merge(adj2);
            em.flush();

            List<InventoryAdjustment> result =
                    repo.findActiveInTransitFor(user1.getId(), medicine.getId(), Inventory.InventoryType.REGULAR_MEDICINE_STOCK);
            assertThat(result).isEmpty();
        }
    }

    // ── findAllUpTo ──────────────────────────────────────────────────────────

    @Nested @DisplayName("findAllUpTo")
    class FindAllUpTo {

        @Test @DisplayName("returns adjustments with adjustedAt < endExclusive")
        void returnsBeforeEnd() {
            // endExclusive = Jan 3 00:00 → adj1 (Jan 1 10:00) and adj2 (Jan 2 10:00) included
            List<InventoryAdjustment> result = repo.findAllUpTo(
                    LocalDateTime.of(2024, 1, 3, 0, 0));
            assertThat(result).extracting(InventoryAdjustment::getId)
                    .containsExactlyInAnyOrder(adj1.getId(), adj2.getId());
        }

        @Test @DisplayName("excludes record at or after endExclusive")
        void excludesAtOrAfterEnd() {
            List<InventoryAdjustment> result = repo.findAllUpTo(JAN_3);
            assertThat(result).noneMatch(a -> a.getId().equals(adj3.getId()));
        }

        @Test @DisplayName("returns all records when end is far in the future")
        void returnsAllWhenFutureEnd() {
            List<InventoryAdjustment> result = repo.findAllUpTo(
                    LocalDateTime.of(2099, 1, 1, 0, 0));
            assertThat(result).extracting(InventoryAdjustment::getId)
                    .containsExactlyInAnyOrder(adj1.getId(), adj2.getId(), adj3.getId());
        }

        @Test @DisplayName("eagerly loads user, medicine, pharmaCompany")
        void eagerlyLoadsAssociations() {
            List<InventoryAdjustment> result = repo.findAllUpTo(
                    LocalDateTime.of(2099, 1, 1, 0, 0));
            assertThat(result.get(0).getMedicine().getPharmaCompany().getName()).isEqualTo("TestPharma");
        }
    }

    // ── findAllFrom ──────────────────────────────────────────────────────────

    @Nested @DisplayName("findAllFrom")
    class FindAllFrom {

        @Test @DisplayName("returns adjustments with adjustedAt >= from (inclusive)")
        void returnsFromInclusive() {
            // from = Jan 2 00:00 → adj2 (Jan 2 10:00) and adj3 (Jan 3 10:00) included
            List<InventoryAdjustment> result = repo.findAllFrom(
                    LocalDateTime.of(2024, 1, 2, 0, 0));
            assertThat(result).extracting(InventoryAdjustment::getId)
                    .containsExactlyInAnyOrder(adj2.getId(), adj3.getId());
        }

        @Test @DisplayName("excludes adjustment before from")
        void excludesBeforeFrom() {
            List<InventoryAdjustment> result = repo.findAllFrom(
                    LocalDateTime.of(2024, 1, 2, 0, 0));
            assertThat(result).noneMatch(a -> a.getId().equals(adj1.getId()));
        }

        @Test @DisplayName("includes record exactly at from boundary")
        void includesExactBoundary() {
            List<InventoryAdjustment> result = repo.findAllFrom(JAN_2); // exactly adj2.adjustedAt
            assertThat(result).anyMatch(a -> a.getId().equals(adj2.getId()));
        }

        @Test @DisplayName("returns empty when from is far in the future")
        void emptyWhenFutureFrom() {
            List<InventoryAdjustment> result = repo.findAllFrom(
                    LocalDateTime.of(2099, 1, 1, 0, 0));
            assertThat(result).isEmpty();
        }
    }

    // ── findWithDetailsBetween ───────────────────────────────────────────────

    @Nested @DisplayName("findWithDetailsBetween")
    class FindWithDetailsBetween {

        @Test @DisplayName("returns adjustments in [start, end) range")
        void returnsWithinRange() {
            List<InventoryAdjustment> result = repo.findWithDetailsBetween(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0));
            assertThat(result).extracting(InventoryAdjustment::getId)
                    .containsExactlyInAnyOrder(adj1.getId(), adj2.getId(), adj3.getId());
        }

        @Test @DisplayName("ordered by adjustedAt DESC")
        void orderedDescending() {
            List<InventoryAdjustment> result = repo.findWithDetailsBetween(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0));
            assertThat(result).extracting(InventoryAdjustment::getId)
                    .containsExactly(adj3.getId(), adj2.getId(), adj1.getId());
        }

        @Test @DisplayName("eagerly loads adjustedBy when present")
        void eagerlyLoadsAdjustedBy() {
            List<InventoryAdjustment> result = repo.findWithDetailsBetween(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0));
            InventoryAdjustment withAdmin = result.stream()
                    .filter(a -> a.getId().equals(adj3.getId()))
                    .findFirst().orElseThrow();
            assertThat(withAdmin.getAdjustedBy()).isNotNull();
            assertThat(withAdmin.getAdjustedBy().getUsername()).isEqualTo("admin");
        }

        @Test @DisplayName("adjustedBy is null for records without an approver (LEFT JOIN)")
        void adjustedByNullForNonAdminRecords() {
            List<InventoryAdjustment> result = repo.findWithDetailsBetween(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0));
            InventoryAdjustment withoutAdmin = result.stream()
                    .filter(a -> a.getId().equals(adj1.getId()))
                    .findFirst().orElseThrow();
            assertThat(withoutAdmin.getAdjustedBy()).isNull();
        }
    }

    // ── nullifyAdjustedBy (@Modifying) ───────────────────────────────────────

    @Nested @DisplayName("nullifyAdjustedBy")
    class NullifyAdjustedBy {

        @Test @DisplayName("sets adjustedBy to null for all records referencing the given user")
        void nullifiesAdjustedBy() {
            repo.nullifyAdjustedBy(admin.getId());
            em.clear();

            InventoryAdjustment found = em.find(InventoryAdjustment.class, adj3.getId());
            assertThat(found.getAdjustedBy()).isNull();
        }

        @Test @DisplayName("does not affect records referencing a different user")
        void doesNotAffectOtherRecords() {
            // adj1 has adjustedBy=null; adj3 has adjustedBy=admin
            // Set adj1's adjustedBy to user1
            adj1.setAdjustedBy(user1);
            em.merge(adj1);
            em.flush();

            repo.nullifyAdjustedBy(admin.getId()); // only admin references should be cleared
            em.clear();

            assertThat(em.find(InventoryAdjustment.class, adj1.getId()).getAdjustedBy())
                    .isNotNull(); // user1 reference untouched
        }

        @Test @DisplayName("no-op when no records reference the given user")
        void noopWhenNoMatch() {
            User stranger = buildUser("stranger", "stranger@test.com", User.Role.USER);
            em.persist(stranger);
            em.flush();

            // Should not throw
            repo.nullifyAdjustedBy(stranger.getId());
            em.clear();

            // adj3 still has admin
            assertThat(em.find(InventoryAdjustment.class, adj3.getId()).getAdjustedBy()).isNotNull();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User buildUser(String username, String email, User.Role role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("secret");
        u.setFullName(username);
        u.setEmail(email);
        u.setRole(role);
        return u;
    }

    private InventoryAdjustment buildAdj(User user, LocalDateTime adjustedAt,
                                          boolean inTransit, User adjustedBy) {
        return InventoryAdjustment.builder()
                .user(user)
                .medicine(medicine)
                .quantity(10)
                .adjustmentType("DISPENSED")
                .internalMovement(false)
                .inTransit(inTransit)
                .wasInTransit(false)
                .inventoryType(Inventory.InventoryType.REGULAR_MEDICINE_STOCK)
                .adjustedAt(adjustedAt)
                .adjustedBy(adjustedBy)
                .build();
    }
}
