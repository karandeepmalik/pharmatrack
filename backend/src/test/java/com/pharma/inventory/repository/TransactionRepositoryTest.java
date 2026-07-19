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

import java.math.BigDecimal;
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
        tx2 = Transaction.builder()
                .submittedBy(user1).medicine(medicine).quantity(BigDecimal.TEN)
                .status(Transaction.TransactionStatus.APPROVED)
                .notes("Test note for APPROVED")
                .submittedAt(LocalDateTime.of(2024, 1, 2, 10, 0))
                .approvedAt(LocalDateTime.of(2024, 1, 2, 12, 0))
                .build();
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
        @DisplayName("returns IDs in ASC submittedAt order (oldest sent for approval first)")
        void returnsAscOrder() {
            Page<Long> page = repo.findAllIds(PageRequest.of(0, 20));
            assertThat(page.getContent()).containsExactly(tx1.getId(), tx2.getId(), tx3.getId());
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
            assertThat(page.getContent()).containsExactly(tx3.getId());
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
        @DisplayName("PENDING results are in ASC submittedAt order (oldest sent for approval first)")
        void pendingInAscOrder() {
            Page<Long> page = repo.findIdsByStatus(Transaction.TransactionStatus.PENDING, PageRequest.of(0, 20));
            assertThat(page.getContent()).containsExactly(tx1.getId(), tx3.getId());
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

    // ── findApprovedBetween ──────────────────────────────────────────────────

    @Nested @DisplayName("findApprovedBetween")
    class FindApprovedBetween {

        @Test @DisplayName("returns APPROVED transactions whose submittedAt falls in [start, end)")
        void returnsApprovedInRange() {
            List<Transaction> result = repo.findApprovedBetween(
                    Transaction.TransactionStatus.APPROVED,
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0));
            assertThat(result).extracting(Transaction::getId).containsExactly(tx2.getId());
        }

        @Test @DisplayName("excludes PENDING transactions even when submittedAt is in range")
        void excludesPending() {
            List<Transaction> result = repo.findApprovedBetween(
                    Transaction.TransactionStatus.APPROVED,
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0));
            assertThat(result).noneMatch(t -> t.getStatus() == Transaction.TransactionStatus.PENDING);
        }

        @Test @DisplayName("returns empty when no APPROVED transactions in range")
        void emptyWhenNoMatch() {
            List<Transaction> result = repo.findApprovedBetween(
                    Transaction.TransactionStatus.APPROVED,
                    LocalDateTime.of(2025, 1, 1, 0, 0),
                    LocalDateTime.of(2025, 1, 2, 0, 0));
            assertThat(result).isEmpty();
        }

        @Test @DisplayName("eagerly loads submittedBy, medicine, pharmaCompany")
        void eagerlyLoadsAssociations() {
            List<Transaction> result = repo.findApprovedBetween(
                    Transaction.TransactionStatus.APPROVED,
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0));
            assertThat(result.get(0).getSubmittedBy().getUsername()).isEqualTo("alice");
            assertThat(result.get(0).getMedicine().getPharmaCompany().getName()).isEqualTo("Cipla");
        }
    }

    // ── findBySubmittedAtBetween ─────────────────────────────────────────────

    @Nested @DisplayName("findBySubmittedAtBetween")
    class FindBySubmittedAtBetween {

        @Test @DisplayName("returns all transactions in [start, end) regardless of status")
        void returnsAllStatusesInRange() {
            // [Jan 1 00:00, Jan 3 00:00) → tx1 (Jan 1) and tx2 (Jan 2) but NOT tx3 (Jan 3 10:00 is NOT < Jan 3 00:00)
            List<Transaction> result = repo.findBySubmittedAtBetween(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 3, 0, 0));
            assertThat(result).extracting(Transaction::getId)
                    .containsExactlyInAnyOrder(tx1.getId(), tx2.getId());
        }

        @Test @DisplayName("excludes transaction at or after the exclusive end boundary")
        void excludesAfterEnd() {
            List<Transaction> result = repo.findBySubmittedAtBetween(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 3, 0, 0));
            assertThat(result).noneMatch(t -> t.getId().equals(tx3.getId()));
        }

        @Test @DisplayName("ordered DESC by submittedAt")
        void orderedDesc() {
            List<Transaction> result = repo.findBySubmittedAtBetween(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0));
            assertThat(result).extracting(Transaction::getId)
                    .containsExactly(tx3.getId(), tx2.getId(), tx1.getId());
        }
    }

    // ── findBySubmittedAtBetweenAndStatus ────────────────────────────────────

    @Nested @DisplayName("findBySubmittedAtBetweenAndStatus")
    class FindBySubmittedAtBetweenAndStatus {

        @Test @DisplayName("filters by status within date range")
        void filtersByStatus() {
            List<Transaction> result = repo.findBySubmittedAtBetweenAndStatus(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0),
                    Transaction.TransactionStatus.PENDING);
            assertThat(result).extracting(Transaction::getId)
                    .containsExactlyInAnyOrder(tx1.getId(), tx3.getId());
        }

        @Test @DisplayName("excludes transactions of a different status")
        void excludesDifferentStatus() {
            List<Transaction> result = repo.findBySubmittedAtBetweenAndStatus(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0),
                    Transaction.TransactionStatus.PENDING);
            assertThat(result).noneMatch(t -> t.getStatus() == Transaction.TransactionStatus.APPROVED);
        }

        @Test @DisplayName("returns empty when status matches but none fall in range")
        void emptyWhenNoneInRange() {
            List<Transaction> result = repo.findBySubmittedAtBetweenAndStatus(
                    LocalDateTime.of(2025, 1, 1, 0, 0),
                    LocalDateTime.of(2025, 2, 1, 0, 0),
                    Transaction.TransactionStatus.PENDING);
            assertThat(result).isEmpty();
        }
    }


    // ── findNonRejectedSubmittedUpToForUser ──────────────────────────────────

    @Nested @DisplayName("findNonRejectedSubmittedUpToForUser")
    class FindNonRejectedSubmittedUpToForUser {

        @Test @DisplayName("returns non-rejected transactions for the user submitted before endExclusive")
        void returnsNonRejectedForUser() {
            // user1 has tx1 (PENDING, Jan1) and tx2 (APPROVED, Jan2); end = Jan 4 → both included
            List<Transaction> result = repo.findNonRejectedSubmittedUpToForUser(
                    user1.getId(), Transaction.TransactionStatus.REJECTED,
                    LocalDateTime.of(2024, 1, 4, 0, 0));
            assertThat(result).extracting(Transaction::getId)
                    .containsExactlyInAnyOrder(tx1.getId(), tx2.getId());
        }

        @Test @DisplayName("excludes transactions from a different user")
        void excludesOtherUsers() {
            List<Transaction> result = repo.findNonRejectedSubmittedUpToForUser(
                    user1.getId(), Transaction.TransactionStatus.REJECTED,
                    LocalDateTime.of(2024, 1, 4, 0, 0));
            assertThat(result).noneMatch(t -> t.getId().equals(tx3.getId()));
        }

        @Test @DisplayName("excludes transactions at or after endExclusive")
        void excludesAtOrAfterEnd() {
            List<Transaction> result = repo.findNonRejectedSubmittedUpToForUser(
                    user1.getId(), Transaction.TransactionStatus.REJECTED,
                    LocalDateTime.of(2024, 1, 2, 10, 0)); // exactly tx2.submittedAt
            assertThat(result).noneMatch(t -> t.getId().equals(tx2.getId()));
        }

        @Test @DisplayName("excludes REJECTED transactions")
        void excludesRejected() {
            tx1.setStatus(Transaction.TransactionStatus.REJECTED);
            em.merge(tx1);
            em.flush();

            List<Transaction> result = repo.findNonRejectedSubmittedUpToForUser(
                    user1.getId(), Transaction.TransactionStatus.REJECTED,
                    LocalDateTime.of(2024, 1, 4, 0, 0));
            assertThat(result).noneMatch(t -> t.getId().equals(tx1.getId()));
        }

        @Test @DisplayName("eagerly loads medicine association")
        void eagerlyLoadsMedicine() {
            List<Transaction> result = repo.findNonRejectedSubmittedUpToForUser(
                    user1.getId(), Transaction.TransactionStatus.REJECTED,
                    LocalDateTime.of(2024, 1, 4, 0, 0));
            assertThat(result.get(0).getMedicine().getName()).isEqualTo("Paracetamol");
        }
    }

    // ── nullifyApprovedBy (@Modifying) ───────────────────────────────────────

    @Nested @DisplayName("nullifyApprovedBy")
    class NullifyApprovedBy {

        @Test @DisplayName("sets approvedBy to null for all transactions referencing the given user")
        void nullifiesApprovedBy() {
            tx1.setApprovedBy(user2);
            em.merge(tx1);
            em.flush();

            repo.nullifyApprovedBy(user2.getId());
            em.clear();

            assertThat(em.find(Transaction.class, tx1.getId()).getApprovedBy()).isNull();
        }

        @Test @DisplayName("does not affect transactions referencing a different approver")
        void doesNotAffectOtherApprovers() {
            tx1.setApprovedBy(user1);
            tx3.setApprovedBy(user2);
            em.merge(tx1);
            em.merge(tx3);
            em.flush();

            repo.nullifyApprovedBy(user2.getId()); // only user2 references cleared
            em.clear();

            assertThat(em.find(Transaction.class, tx1.getId()).getApprovedBy()).isNotNull();
        }

        @Test @DisplayName("no-op when no transactions reference the given user")
        void noopWhenNoMatch() {
            User stranger = buildUser("stranger", "stranger@test.com");
            em.persist(stranger);
            em.flush();

            // Should not throw
            repo.nullifyApprovedBy(stranger.getId());
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
                .quantity(BigDecimal.TEN)
                .status(status)
                .notes("Test note for " + status)
                .submittedAt(submittedAt)
                .build();
    }
}
