package com.pharma.inventory.repository;

import com.pharma.inventory.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @DataJpaTest boots H2 + JPA + all repositories and validates every @Query JPQL
 * at EntityManagerFactory creation time — before any test method runs.
 *
 * This class would have caught the original DISTINCT+ORDER BY violation
 * (SELECT DISTINCT t.id ... ORDER BY t.submittedAt) which Hibernate rejects
 * because the ORDER BY column must appear in the SELECT list when DISTINCT is used.
 */
@DataJpaTest
@DisplayName("TransactionRepository JPQL integration")
class TransactionRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired TransactionRepository repo;

    private User user1;
    private User user2;
    private Transaction tx1; // user1, PENDING,  oldest
    private Transaction tx2; // user1, APPROVED, middle
    private Transaction tx3; // user2, PENDING,  newest

    @BeforeEach
    void setUp() {
        PharmaCompany company = new PharmaCompany();
        company.setName("Cipla");
        em.persist(company);

        Medicine medicine = Medicine.builder()
                .name("Paracetamol")
                .type(Medicine.MedicineType.TABLET)
                .specification(500.0)
                .price(10)
                .pharmaCompany(company)
                .build();
        em.persist(medicine);

        user1 = buildUser("alice", "alice@test.com");
        user2 = buildUser("bob",   "bob@test.com");
        em.persist(user1);
        em.persist(user2);

        // Explicit submittedAt values so ordering is deterministic
        tx1 = buildTx(user1, medicine, Transaction.TransactionStatus.PENDING,
                LocalDateTime.of(2024, 1, 1, 10, 0));
        tx2 = buildTx(user1, medicine, Transaction.TransactionStatus.APPROVED,
                LocalDateTime.of(2024, 1, 2, 10, 0));
        tx3 = buildTx(user2, medicine, Transaction.TransactionStatus.PENDING,
                LocalDateTime.of(2024, 1, 3, 10, 0));
        em.persist(tx1);
        em.persist(tx2);
        em.persist(tx3);
        em.flush();
    }

    // ── findAllIds ───────────────────────────────────────────────────────────

    @Nested @DisplayName("findAllIds")
    class FindAllIds {

        @Test
        @DisplayName("returns all transaction IDs")
        void returnsAllIds() {
            Page<Long> page = repo.findAllIds(PageRequest.of(0, 20));
            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getContent()).containsExactlyInAnyOrder(tx1.getId(), tx2.getId(), tx3.getId());
        }

        @Test
        @DisplayName("returns IDs in DESC submittedAt order")
        void returnsDescOrder() {
            Page<Long> page = repo.findAllIds(PageRequest.of(0, 20));
            assertThat(page.getContent()).containsExactly(tx3.getId(), tx2.getId(), tx1.getId());
        }

        @Test
        @DisplayName("paginates correctly — page 0 of size 2 returns 2 items, totalElements still 3")
        void paginatesCorrectly() {
            Page<Long> page = repo.findAllIds(PageRequest.of(0, 2));
            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getTotalPages()).isEqualTo(2);
            assertThat(page.isLast()).isFalse();
        }

        @Test
        @DisplayName("last page has remaining items")
        void lastPageHasRemainder() {
            Page<Long> page = repo.findAllIds(PageRequest.of(1, 2));
            assertThat(page.getContent()).containsExactly(tx1.getId());
            assertThat(page.isLast()).isTrue();
        }
    }

    // ── findIdsByStatus ──────────────────────────────────────────────────────

    @Nested @DisplayName("findIdsByStatus")
    class FindIdsByStatus {

        @Test
        @DisplayName("filters to PENDING only")
        void filtersPending() {
            Page<Long> page = repo.findIdsByStatus(Transaction.TransactionStatus.PENDING, PageRequest.of(0, 20));
            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).containsExactlyInAnyOrder(tx1.getId(), tx3.getId());
        }

        @Test
        @DisplayName("filters to APPROVED only")
        void filtersApproved() {
            Page<Long> page = repo.findIdsByStatus(Transaction.TransactionStatus.APPROVED, PageRequest.of(0, 20));
            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent()).containsExactly(tx2.getId());
        }

        @Test
        @DisplayName("returns empty page when no transactions match status")
        void emptyWhenNoMatch() {
            Page<Long> page = repo.findIdsByStatus(Transaction.TransactionStatus.REJECTED, PageRequest.of(0, 20));
            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
        }

        @Test
        @DisplayName("PENDING results are in DESC submittedAt order")
        void pendingInDescOrder() {
            Page<Long> page = repo.findIdsByStatus(Transaction.TransactionStatus.PENDING, PageRequest.of(0, 20));
            assertThat(page.getContent()).containsExactly(tx3.getId(), tx1.getId());
        }
    }

    // ── findIdsByUser ────────────────────────────────────────────────────────

    @Nested @DisplayName("findIdsByUser")
    class FindIdsByUser {

        @Test
        @DisplayName("filters to user1 transactions only")
        void filtersUser1() {
            Page<Long> page = repo.findIdsByUser(user1, PageRequest.of(0, 20));
            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).containsExactlyInAnyOrder(tx1.getId(), tx2.getId());
        }

        @Test
        @DisplayName("filters to user2 transactions only")
        void filtersUser2() {
            Page<Long> page = repo.findIdsByUser(user2, PageRequest.of(0, 20));
            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent()).containsExactly(tx3.getId());
        }

        @Test
        @DisplayName("returns empty page for user with no transactions")
        void emptyForUserWithNoTx() {
            User user3 = buildUser("carol", "carol@test.com");
            em.persist(user3);
            em.flush();

            Page<Long> page = repo.findIdsByUser(user3, PageRequest.of(0, 20));
            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
        }

        @Test
        @DisplayName("user1 results are in DESC submittedAt order")
        void user1InDescOrder() {
            Page<Long> page = repo.findIdsByUser(user1, PageRequest.of(0, 20));
            assertThat(page.getContent()).containsExactly(tx2.getId(), tx1.getId());
        }
    }

    // ── findByIdsWithDetails ─────────────────────────────────────────────────

    @Nested @DisplayName("findByIdsWithDetails")
    class FindByIdsWithDetails {

        @Test
        @DisplayName("loads all requested transactions")
        void loadsRequestedTransactions() {
            List<Long> ids = List.of(tx1.getId(), tx3.getId());
            List<Transaction> result = repo.findByIdsWithDetails(ids);
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("eagerly loads submittedBy association (no LazyInitializationException)")
        void loadsSubmittedBy() {
            List<Transaction> result = repo.findByIdsWithDetails(List.of(tx1.getId()));
            assertThat(result.get(0).getSubmittedBy().getUsername()).isEqualTo("alice");
        }

        @Test
        @DisplayName("eagerly loads medicine association")
        void loadsMedicine() {
            List<Transaction> result = repo.findByIdsWithDetails(List.of(tx1.getId()));
            assertThat(result.get(0).getMedicine().getName()).isEqualTo("Paracetamol");
        }

        @Test
        @DisplayName("eagerly loads pharmaCompany via medicine")
        void loadsPharmaCompany() {
            List<Transaction> result = repo.findByIdsWithDetails(List.of(tx1.getId()));
            assertThat(result.get(0).getMedicine().getPharmaCompany().getName()).isEqualTo("Cipla");
        }

        @Test
        @DisplayName("returns results in DESC submittedAt order")
        void returnsDescOrder() {
            List<Long> ids = List.of(tx1.getId(), tx2.getId(), tx3.getId());
            List<Transaction> result = repo.findByIdsWithDetails(ids);
            assertThat(result).extracting(Transaction::getId)
                    .containsExactly(tx3.getId(), tx2.getId(), tx1.getId());
        }

        @Test
        @DisplayName("returns empty list when given empty ID list")
        void emptyIds_returnsEmpty() {
            List<Transaction> result = repo.findByIdsWithDetails(List.of());
            assertThat(result).isEmpty();
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

    private Transaction buildTx(User user, Medicine medicine,
                                 Transaction.TransactionStatus status,
                                 LocalDateTime submittedAt) {
        return Transaction.builder()
                .submittedBy(user)
                .medicine(medicine)
                .quantity(10)
                .status(status)
                .notes("Test note for " + status)
                .submittedAt(submittedAt)
                .build();
    }
}
