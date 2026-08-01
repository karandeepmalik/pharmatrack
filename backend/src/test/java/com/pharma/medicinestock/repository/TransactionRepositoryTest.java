package com.pharma.medicinestock.repository;

import com.pharma.medicinestock.entity.*;
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
import java.util.ArrayList;
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
    private Medicine medicine;
    private Medicine medicine2;
    private Transaction tx1; // user1, PENDING,  oldest
    private Transaction tx2; // user1, APPROVED, middle
    private Transaction tx3; // user2, PENDING,  newest

    @BeforeEach
    void setUp() {
        PharmaCompany company = new PharmaCompany();
        company.setName("Cipla");
        em.persist(company);

        medicine = Medicine.builder()
                .name("Paracetamol")
                .type(Medicine.MedicineType.TABLET)
                .specification(500.0)
                .price(10)
                .pharmaCompany(company)
                .build();
        em.persist(medicine);

        medicine2 = Medicine.builder()
                .name("Ibuprofen")
                .type(Medicine.MedicineType.TABLET)
                .specification(400.0)
                .price(15)
                .pharmaCompany(company)
                .build();
        em.persist(medicine2);

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
        tx3 = buildTx(user2, medicine2, Transaction.TransactionStatus.PENDING,
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
        @DisplayName("returns IDs in DESC submittedAt order (most recently submitted first)")
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

        @Test
        @DisplayName("rows with an identical submittedAt paginate without duplicating or dropping any "
                + "row — see searchHistory's identical test for why this matters")
        void identicalSubmittedAtPaginatesWithoutDuplicatesOrGaps() {
            LocalDateTime sameInstant = LocalDateTime.of(2024, 6, 1, 0, 0);
            Transaction tied1 = buildTx(user1, medicine, Transaction.TransactionStatus.PENDING, sameInstant);
            Transaction tied2 = buildTx(user1, medicine, Transaction.TransactionStatus.PENDING, sameInstant);
            em.persist(tied1); em.persist(tied2);
            em.flush();

            Page<Long> page0 = repo.findAllIds(PageRequest.of(0, 4));
            Page<Long> page1 = repo.findAllIds(PageRequest.of(1, 4));

            assertThat(page0.getContent()).doesNotContainAnyElementsOf(page1.getContent());
            List<Long> allIds = new ArrayList<>(page0.getContent());
            allIds.addAll(page1.getContent());
            assertThat(allIds).containsExactlyInAnyOrder(tx1.getId(), tx2.getId(), tx3.getId(), tied1.getId(), tied2.getId());
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
        @DisplayName("PENDING results are in DESC submittedAt order (most recently submitted first)")
        void pendingInDescOrder() {
            Page<Long> page = repo.findIdsByStatus(Transaction.TransactionStatus.PENDING, PageRequest.of(0, 20));
            assertThat(page.getContent()).containsExactly(tx3.getId(), tx1.getId());
        }
    }

    // ── searchMyHistory ──────────────────────────────────────────────────────

    @Nested @DisplayName("searchMyHistory")
    class SearchMyHistory {

        @Test
        @DisplayName("filters to user1 transactions only")
        void filtersUser1() {
            Page<Transaction> page = repo.searchMyHistory(user1, null, null, null, PageRequest.of(0, 20));
            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).extracting(Transaction::getId)
                    .containsExactlyInAnyOrder(tx1.getId(), tx2.getId());
        }

        @Test
        @DisplayName("filters to user2 transactions only")
        void filtersUser2() {
            Page<Transaction> page = repo.searchMyHistory(user2, null, null, null, PageRequest.of(0, 20));
            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent()).extracting(Transaction::getId).containsExactly(tx3.getId());
        }

        @Test
        @DisplayName("returns empty page for user with no transactions")
        void emptyForUserWithNoTx() {
            User user3 = buildUser("carol", "carol@test.com");
            em.persist(user3);
            em.flush();

            Page<Transaction> page = repo.searchMyHistory(user3, null, null, null, PageRequest.of(0, 20));
            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
        }

        @Test
        @DisplayName("user1 results are in DESC submittedAt order")
        void user1InDescOrder() {
            Page<Transaction> page = repo.searchMyHistory(user1, null, null, null, PageRequest.of(0, 20));
            assertThat(page.getContent()).extracting(Transaction::getId)
                    .containsExactly(tx2.getId(), tx1.getId());
        }

        @Test
        @DisplayName("filters by status within a user's own transactions")
        void filtersByStatus() {
            Page<Transaction> page = repo.searchMyHistory(
                    user1, Transaction.TransactionStatus.APPROVED, null, null, PageRequest.of(0, 20));
            assertThat(page.getContent()).extracting(Transaction::getId).containsExactly(tx2.getId());
        }

        @Test
        @DisplayName("filters by exact medicineId within a user's own transactions")
        void filtersByMedicineId() {
            Page<Transaction> page = repo.searchMyHistory(user1, null, medicine2.getId(), null, PageRequest.of(0, 20));
            assertThat(page.getContent()).isEmpty();
        }

        @Test
        @DisplayName("filters by a pre-built LIKE pattern, case-insensitively")
        void filtersByNotes() {
            // notesPattern arrives as a complete, already-lowercased LIKE pattern (built by the
            // service layer) — see searchHistory's Javadoc for why the query itself does no
            // LOWER()/CONCAT() on the bind parameter.
            Page<Transaction> page = repo.searchMyHistory(user1, null, null, "%approved%", PageRequest.of(0, 20));
            assertThat(page.getContent()).extracting(Transaction::getId).containsExactly(tx2.getId());
        }

        @Test
        @DisplayName("never returns another user's transactions regardless of filters")
        void neverLeaksOtherUsersTransactions() {
            Page<Transaction> page = repo.searchMyHistory(user1, null, null, null, PageRequest.of(0, 20));
            assertThat(page.getContent()).noneMatch(t -> t.getId().equals(tx3.getId()));
        }

        @Test
        @DisplayName("a filter that matches nothing on the current page still matches records beyond it — "
                + "the whole point of doing this server-side instead of client-side against loaded pages only")
        void filterMatchesRecordOutsideFirstPage() {
            // page size 1: tx2 (newest of user1's) is the only item on page 0. Filtering by
            // PENDING (only matches tx1, the older one) still finds it even at a page size that
            // would otherwise paginate it out.
            Page<Transaction> page = repo.searchMyHistory(
                    user1, Transaction.TransactionStatus.PENDING, null, null, PageRequest.of(0, 1));
            assertThat(page.getContent()).extracting(Transaction::getId).containsExactly(tx1.getId());
            assertThat(page.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("rows with an identical submittedAt paginate without duplicating or dropping any "
                + "row — see searchHistory's identical test for why this matters")
        void identicalSubmittedAtPaginatesWithoutDuplicatesOrGaps() {
            LocalDateTime sameInstant = LocalDateTime.of(2024, 6, 1, 0, 0);
            Transaction tied1 = buildTx(user1, medicine, Transaction.TransactionStatus.PENDING, sameInstant);
            Transaction tied2 = buildTx(user1, medicine, Transaction.TransactionStatus.PENDING, sameInstant);
            Transaction tied3 = buildTx(user1, medicine, Transaction.TransactionStatus.PENDING, sameInstant);
            em.persist(tied1); em.persist(tied2); em.persist(tied3);
            em.flush();

            // user1 has 5 matching transactions total (tx1, tx2, tied1-3) — page size 3 covers
            // all of them across exactly two pages.
            Page<Transaction> page0 = repo.searchMyHistory(user1, null, null, null, PageRequest.of(0, 3));
            Page<Transaction> page1 = repo.searchMyHistory(user1, null, null, null, PageRequest.of(1, 3));

            List<Long> page0Ids = page0.getContent().stream().map(Transaction::getId).toList();
            List<Long> page1Ids = page1.getContent().stream().map(Transaction::getId).toList();
            assertThat(page0Ids).doesNotContainAnyElementsOf(page1Ids);

            List<Long> allIds = new ArrayList<>(page0Ids);
            allIds.addAll(page1Ids);
            assertThat(allIds).containsExactlyInAnyOrder(
                    tx1.getId(), tx2.getId(), tied1.getId(), tied2.getId(), tied3.getId());
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

    // ── searchHistory ─────────────────────────────────────────────────────────

    @Nested @DisplayName("searchHistory")
    class SearchHistory {

        @Test @DisplayName("returns all transactions in [start, end) regardless of status when status is null")
        void returnsAllStatusesInRange() {
            // [Jan 1 00:00, Jan 3 00:00) → tx1 (Jan 1) and tx2 (Jan 2) but NOT tx3 (Jan 3 10:00 is NOT < Jan 3 00:00)
            Page<Transaction> result = repo.searchHistory(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 3, 0, 0),
                    null, null, null, null,
                    PageRequest.of(0, 20));
            assertThat(result.getContent()).extracting(Transaction::getId)
                    .containsExactlyInAnyOrder(tx1.getId(), tx2.getId());
        }

        @Test @DisplayName("excludes transaction at or after the exclusive end boundary")
        void excludesAfterEnd() {
            Page<Transaction> result = repo.searchHistory(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 3, 0, 0),
                    null, null, null, null,
                    PageRequest.of(0, 20));
            assertThat(result.getContent()).noneMatch(t -> t.getId().equals(tx3.getId()));
        }

        @Test @DisplayName("ordered DESC by submittedAt")
        void orderedDesc() {
            Page<Transaction> result = repo.searchHistory(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0),
                    null, null, null, null,
                    PageRequest.of(0, 20));
            assertThat(result.getContent()).extracting(Transaction::getId)
                    .containsExactly(tx3.getId(), tx2.getId(), tx1.getId());
        }

        @Test @DisplayName("paginates correctly — page 0 of size 2 returns 2 items, totalElements still 3")
        void paginatesCorrectly() {
            Page<Transaction> result = repo.searchHistory(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0),
                    null, null, null, null,
                    PageRequest.of(0, 2));
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.isLast()).isFalse();
        }

        @Test @DisplayName("filters by status within date range")
        void filtersByStatus() {
            Page<Transaction> result = repo.searchHistory(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0),
                    Transaction.TransactionStatus.PENDING, null, null, null,
                    PageRequest.of(0, 20));
            assertThat(result.getContent()).extracting(Transaction::getId)
                    .containsExactlyInAnyOrder(tx1.getId(), tx3.getId());
        }

        @Test @DisplayName("excludes transactions of a different status")
        void excludesDifferentStatus() {
            Page<Transaction> result = repo.searchHistory(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0),
                    Transaction.TransactionStatus.PENDING, null, null, null,
                    PageRequest.of(0, 20));
            assertThat(result.getContent()).noneMatch(t -> t.getStatus() == Transaction.TransactionStatus.APPROVED);
        }

        @Test @DisplayName("returns empty when status matches but none fall in range")
        void emptyWhenNoneInRange() {
            Page<Transaction> result = repo.searchHistory(
                    LocalDateTime.of(2025, 1, 1, 0, 0),
                    LocalDateTime.of(2025, 2, 1, 0, 0),
                    Transaction.TransactionStatus.PENDING, null, null, null,
                    PageRequest.of(0, 20));
            assertThat(result.getContent()).isEmpty();
        }

        @Test @DisplayName("filters by exact username")
        void filtersByUsername() {
            Page<Transaction> result = repo.searchHistory(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0),
                    null, "bob", null, null,
                    PageRequest.of(0, 20));
            assertThat(result.getContent()).extracting(Transaction::getId).containsExactly(tx3.getId());
        }

        @Test @DisplayName("filters by exact medicineId")
        void filtersByMedicineId() {
            Page<Transaction> result = repo.searchHistory(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0),
                    null, null, medicine2.getId(), null,
                    PageRequest.of(0, 20));
            assertThat(result.getContent()).extracting(Transaction::getId).containsExactly(tx3.getId());
        }

        @Test @DisplayName("filters by a pre-built LIKE pattern, case-insensitively")
        void filtersByNotes() {
            // notesPattern arrives as a complete, already-lowercased LIKE pattern (built by the
            // service layer) — the query itself does no LOWER()/CONCAT() on the bind parameter.
            // See searchHistory's Javadoc for why: doing that at the SQL level breaks on Postgres
            // when the parameter is null, which H2 (used here) doesn't reproduce.
            Page<Transaction> result = repo.searchHistory(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0),
                    null, null, null, "%approved%",
                    PageRequest.of(0, 20));
            assertThat(result.getContent()).extracting(Transaction::getId).containsExactly(tx2.getId());
        }

        @Test @DisplayName("combines username and status filters")
        void combinesUsernameAndStatus() {
            Page<Transaction> result = repo.searchHistory(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0),
                    Transaction.TransactionStatus.PENDING, "alice", null, null,
                    PageRequest.of(0, 20));
            assertThat(result.getContent()).extracting(Transaction::getId).containsExactly(tx1.getId());
        }

        @Test
        @DisplayName("a filter that matches nothing on the current page still matches records beyond it — "
                + "the whole point of doing this server-side instead of client-side against loaded pages only")
        void filterMatchesRecordOutsideFirstPage() {
            // page size 1: tx3 (newest) is the only item on page 0. Filtering by "bob" (only on
            // tx3) still finds it even at a page size that would otherwise paginate it out —
            // proving the filter runs against the full server-side result set, not just page 0.
            Page<Transaction> result = repo.searchHistory(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0),
                    null, "alice", null, null,
                    PageRequest.of(0, 1));
            assertThat(result.getContent()).extracting(Transaction::getId).containsExactly(tx2.getId());
            assertThat(result.getTotalElements()).isEqualTo(2); // tx1 + tx2, both alice
        }

        @Test
        @DisplayName("rows with an identical submittedAt (e.g. same dispatch date) paginate without "
                + "duplicating or dropping any row across separate page fetches — a real, shipped bug: "
                + "without a secondary tiebreaker, Postgres doesn't guarantee a stable order for ties "
                + "across separate LIMIT/OFFSET queries, so scrolling from page 0 to page 1 could return "
                + "the same tied row twice while silently skipping a different one")
        void identicalSubmittedAtPaginatesWithoutDuplicatesOrGaps() {
            LocalDateTime sameInstant = LocalDateTime.of(2024, 6, 1, 0, 0);
            Transaction tied1 = buildTx(user1, medicine, Transaction.TransactionStatus.PENDING, sameInstant);
            Transaction tied2 = buildTx(user1, medicine, Transaction.TransactionStatus.PENDING, sameInstant);
            Transaction tied3 = buildTx(user1, medicine, Transaction.TransactionStatus.PENDING, sameInstant);
            em.persist(tied1); em.persist(tied2); em.persist(tied3);
            em.flush();

            Page<Transaction> page0 = repo.searchHistory(
                    sameInstant, sameInstant.plusDays(1), null, null, null, null, PageRequest.of(0, 2));
            Page<Transaction> page1 = repo.searchHistory(
                    sameInstant, sameInstant.plusDays(1), null, null, null, null, PageRequest.of(1, 2));

            List<Long> page0Ids = page0.getContent().stream().map(Transaction::getId).toList();
            List<Long> page1Ids = page1.getContent().stream().map(Transaction::getId).toList();

            assertThat(page0Ids).hasSize(2);
            assertThat(page1Ids).hasSize(1);
            assertThat(page0Ids).doesNotContainAnyElementsOf(page1Ids); // no row duplicated across pages
            assertThat(page0Ids).containsExactlyInAnyOrder(tied3.getId(), tied2.getId()); // id DESC tiebreak
            assertThat(page1Ids).containsExactly(tied1.getId()); // the one row not dropped
        }
    }

    // ── sumQuantityForHistory ────────────────────────────────────────────────

    @Nested @DisplayName("sumQuantityForHistory")
    class SumQuantityForHistory {

        @Test @DisplayName("sums quantity across all matching transactions regardless of status when status is null")
        void sumsAllStatusesInRange() {
            // tx1 (10) + tx2 (10) in [Jan 1, Jan 3); tx3 excluded by the date boundary
            BigDecimal result = repo.sumQuantityForHistory(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 3, 0, 0),
                    null, null, null, null);
            assertThat(result).isEqualByComparingTo("20");
        }

        @Test @DisplayName("matches exactly what searchHistory itself returns for the same filters")
        void matchesSearchHistoryResultSet() {
            Page<Transaction> page = repo.searchHistory(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0),
                    Transaction.TransactionStatus.PENDING, null, null, null,
                    PageRequest.of(0, 20));
            BigDecimal expected = page.getContent().stream()
                    .map(Transaction::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal result = repo.sumQuantityForHistory(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0),
                    Transaction.TransactionStatus.PENDING, null, null, null);

            assertThat(result).isEqualByComparingTo(expected);
        }

        @Test @DisplayName("returns zero (not null) when nothing matches")
        void returnsZeroWhenNoMatch() {
            BigDecimal result = repo.sumQuantityForHistory(
                    LocalDateTime.of(2025, 1, 1, 0, 0),
                    LocalDateTime.of(2025, 2, 1, 0, 0),
                    null, null, null, null);
            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test @DisplayName("filters by exact username, same as searchHistory")
        void filtersByUsername() {
            // tx3 (bob, 10) is the only match
            BigDecimal result = repo.sumQuantityForHistory(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0),
                    null, "bob", null, null);
            assertThat(result).isEqualByComparingTo("10");
        }

        @Test @DisplayName("reflects the full matching set, not just one page's worth")
        void reflectsFullSetNotJustOnePage() {
            // page size 1 would only return 1 of alice's 2 transactions, but the sum must
            // still cover both — the whole point of computing this server-side.
            BigDecimal result = repo.sumQuantityForHistory(
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 1, 4, 0, 0),
                    null, "alice", null, null);
            assertThat(result).isEqualByComparingTo("20");
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
