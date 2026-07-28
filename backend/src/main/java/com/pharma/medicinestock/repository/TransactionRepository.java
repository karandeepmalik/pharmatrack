package com.pharma.medicinestock.repository;
import com.pharma.medicinestock.entity.Transaction;
import com.pharma.medicinestock.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction,Long> {

    void deleteBySubmittedById(Long userId);

    @Modifying
    @Query("UPDATE Transaction t SET t.approvedBy = null WHERE t.approvedBy.id = :userId")
    void nullifyApprovedBy(Long userId);

    /**
     * Two-query pagination strategy for transactions (avoids HibernateException
     * "firstResult/maxResults specified with collection fetch" when JOIN FETCH + Pageable
     * are combined directly).
     *
     * Step 1: fetch a page of IDs via lightweight scalar queries.
     * Step 2: fetch the full entity graph for those IDs (findByIdsWithDetails).
     *
     * Every ORDER BY below breaks submittedAt ties with id DESC — submittedAt is truncated to
     * the dispatch date (not wall-clock submission time), so many rows legitimately share the
     * exact same value, and Postgres does not guarantee a stable order for tied rows across
     * separate LIMIT/OFFSET query executions. Without a secondary deterministic key, scrolling
     * from one page to the next could return the same tied row twice (it reordered within its
     * tie-group between the two queries) while silently dropping a different one — a real,
     * shipped bug reported against "View Past Medicine Dispatches".
     */
    /** Admin "Review Adjustments" queue — DESC so the most recently submitted requests surface first. */
    @Query(value = "SELECT t.id FROM Transaction t ORDER BY t.submittedAt DESC, t.id DESC",
           countQuery = "SELECT COUNT(t) FROM Transaction t")
    Page<Long> findAllIds(Pageable pageable);

    /** Admin "Review Adjustments" queue — DESC so the most recently submitted requests surface first. */
    @Query(value = "SELECT t.id FROM Transaction t WHERE t.status = :status ORDER BY t.submittedAt DESC, t.id DESC",
           countQuery = "SELECT COUNT(t) FROM Transaction t WHERE t.status = :status")
    Page<Long> findIdsByStatus(@Param("status") Transaction.TransactionStatus status, Pageable pageable);

    /**
     * A single user's own transactions, with optional status/medicine/notes filters — backs the
     * paginated "Medicine Dispatch History" endpoint (GET /transactions/my). Same nullable-
     * predicate pattern as searchHistory below: passing null for a filter makes its clause always
     * true. Filtering happens here (server-side, against the full matching set) rather than
     * client-side over whatever page the infinite-scroll happened to have loaded so far —
     * MyTransactions.jsx had exactly the shipped-regression bug searchHistory's Javadoc
     * describes for its own filters, fixed to match.
     *
     * notesPattern must arrive pre-built as a full lowercased LIKE pattern — see searchHistory's
     * Javadoc for why (Postgres can't infer a type for a bind parameter used only as a LOWER()/
     * CONCAT() argument when it's null, and 500s).
     *
     * Safe to paginate directly (no two-query id workaround needed, unlike findAllIds/
     * findIdsByStatus above): every JOIN FETCH here is a to-one association.
     *
     * ORDER BY breaks submittedAt ties with id DESC — see findAllIds' Javadoc above for why
     * that's required for correct pagination, not just tidiness.
     */
    @Query(value = "SELECT t FROM Transaction t " +
           "JOIN FETCH t.submittedBy u JOIN FETCH t.medicine m JOIN FETCH m.pharmaCompany " +
           "LEFT JOIN FETCH t.approvedBy " +
           "WHERE t.submittedBy = :user " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (:medicineId IS NULL OR m.id = :medicineId) " +
           "AND (:notesPattern IS NULL OR LOWER(t.notes) LIKE :notesPattern) " +
           "ORDER BY t.submittedAt DESC, t.id DESC",
           countQuery = "SELECT COUNT(t) FROM Transaction t " +
           "WHERE t.submittedBy = :user " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (:medicineId IS NULL OR t.medicine.id = :medicineId) " +
           "AND (:notesPattern IS NULL OR LOWER(t.notes) LIKE :notesPattern)")
    Page<Transaction> searchMyHistory(
            @Param("user") User user,
            @Param("status") Transaction.TransactionStatus status,
            @Param("medicineId") Long medicineId,
            @Param("notesPattern") String notesPattern,
            Pageable pageable);

    @Query("SELECT DISTINCT t FROM Transaction t " +
           "JOIN FETCH t.submittedBy " +
           "JOIN FETCH t.medicine m " +
           "JOIN FETCH m.pharmaCompany " +
           "LEFT JOIN FETCH t.approvedBy " +
           "WHERE t.id IN :ids " +
           "ORDER BY t.submittedAt DESC")
    List<Transaction> findByIdsWithDetails(@Param("ids") List<Long> ids);

    /** Approved transactions whose dispatch date (submittedAt) falls on a given day — used by daily report. */
    @Query("SELECT t FROM Transaction t JOIN FETCH t.submittedBy u JOIN FETCH t.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE t.status = :status AND t.submittedAt >= :start AND t.submittedAt < :end " +
           "ORDER BY u.fullName, m.name")
    List<Transaction> findApprovedBetween(
            @Param("status") Transaction.TransactionStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * All transactions submitted in a date range, with optional status/username/medicine/notes
     * filters — backs the paginated admin history endpoint ("View Past Medicine Dispatches").
     * Every filter param is nullable: passing null for a param makes its "(:param IS NULL OR ...)"
     * clause always true, so the caller only needs one method regardless of which filters are
     * actually in use. Filtering happens here (server-side, against the full matching set) rather
     * than being left to the frontend to apply against whatever page happens to be loaded —
     * without this, a wide date range with more results than one page would silently hide
     * real matches that just hadn't been scrolled into view yet.
     *
     * notesPattern must arrive pre-built as a full lowercased LIKE pattern (e.g. "%clinic%"), not
     * as a raw substring wrapped in LOWER(CONCAT(...)) here: Postgres's JDBC driver can't infer a
     * type for a bind parameter passed as an argument to CONCAT/LOWER when its value is null
     * (which it is on every call where no notes filter is active) and defaults it to bytea,
     * producing "ERROR: function lower(bytea) does not exist" at query-prepare time — confirmed
     * live against Postgres (H2, used in tests, does not reproduce this). Building the whole
     * pattern in Java and binding it as a plain string against the LIKE operator avoids the
     * ambiguity entirely.
     *
     * Safe to paginate directly (no two-query workaround needed): every JOIN FETCH here is a
     * to-one association, so there's no collection-fetch + firstResult/maxResults conflict.
     *
     * ORDER BY breaks submittedAt ties with id DESC — see findAllIds' Javadoc above for why
     * that's required for correct pagination, not just tidiness.
     */
    @Query(value = "SELECT t FROM Transaction t " +
           "JOIN FETCH t.submittedBy u JOIN FETCH t.medicine m JOIN FETCH m.pharmaCompany " +
           "LEFT JOIN FETCH t.approvedBy " +
           "WHERE t.submittedAt >= :start AND t.submittedAt < :end " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (:username IS NULL OR u.username = :username) " +
           "AND (:medicineId IS NULL OR m.id = :medicineId) " +
           "AND (:notesPattern IS NULL OR LOWER(t.notes) LIKE :notesPattern) " +
           "ORDER BY t.submittedAt DESC, t.id DESC",
           countQuery = "SELECT COUNT(t) FROM Transaction t " +
           "WHERE t.submittedAt >= :start AND t.submittedAt < :end " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (:username IS NULL OR t.submittedBy.username = :username) " +
           "AND (:medicineId IS NULL OR t.medicine.id = :medicineId) " +
           "AND (:notesPattern IS NULL OR LOWER(t.notes) LIKE :notesPattern)")
    Page<Transaction> searchHistory(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("status") Transaction.TransactionStatus status,
            @Param("username") String username,
            @Param("medicineId") Long medicineId,
            @Param("notesPattern") String notesPattern,
            Pageable pageable);

    /**
     * All non-rejected transactions submitted before endExclusive.
     * Mirrors how the MedicineStock table works: stock is deducted at submission time and
     * only added back on rejection — so PENDING + APPROVED txs both reduce stock.
     */
    @Query("SELECT t FROM Transaction t " +
           "JOIN FETCH t.submittedBy u JOIN FETCH t.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE t.status != :rejected AND t.submittedAt < :endExclusive " +
           "ORDER BY u.fullName, m.name")
    List<Transaction> findNonRejectedSubmittedUpTo(
            @Param("rejected") Transaction.TransactionStatus rejected,
            @Param("endExclusive") LocalDateTime endExclusive);

    /**
     * All non-rejected transactions for one user, submitted before endExclusive — the
     * transaction side of the same forward reconstruction ReportService uses for historical
     * reports, applied here to compute current dispatchable stock.
     */
    @Query("SELECT t FROM Transaction t " +
           "JOIN FETCH t.medicine " +
           "WHERE t.submittedBy.id = :userId AND t.status != :rejected AND t.submittedAt < :endExclusive")
    List<Transaction> findNonRejectedSubmittedUpToForUser(
            @Param("userId") Long userId,
            @Param("rejected") Transaction.TransactionStatus rejected,
            @Param("endExclusive") LocalDateTime endExclusive);
}
