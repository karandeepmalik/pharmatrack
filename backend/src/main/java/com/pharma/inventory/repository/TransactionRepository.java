package com.pharma.inventory.repository;
import com.pharma.inventory.entity.Transaction;
import com.pharma.inventory.entity.User;
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
     * All transactions with their submitter, medicine, pharma company, and approver
     * loaded in a single query — eliminates N+1 on those relations.
     * Screenshots are LAZY + @BatchSize(50) on the entity, so they load in batches
     * as the mapper accesses them within the same transaction.
     */
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "JOIN FETCH t.submittedBy " +
           "JOIN FETCH t.medicine m " +
           "JOIN FETCH m.pharmaCompany " +
           "LEFT JOIN FETCH t.approvedBy " +
           "ORDER BY t.submittedAt DESC")
    List<Transaction> findAllWithDetails();

    /** Same join-fetch strategy filtered to a specific user. */
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "JOIN FETCH t.submittedBy " +
           "JOIN FETCH t.medicine m " +
           "JOIN FETCH m.pharmaCompany " +
           "LEFT JOIN FETCH t.approvedBy " +
           "WHERE t.submittedBy = :user " +
           "ORDER BY t.submittedAt DESC")
    List<Transaction> findByUserWithDetails(@Param("user") User user);

    /**
     * Two-query pagination strategy for transactions (avoids HibernateException
     * "firstResult/maxResults specified with collection fetch" when JOIN FETCH + Pageable
     * are combined directly).
     *
     * Step 1: fetch a page of IDs via lightweight scalar queries.
     * Step 2: fetch the full entity graph for those IDs (findByIdsWithDetails).
     */
    @Query(value = "SELECT t.id FROM Transaction t ORDER BY t.submittedAt DESC",
           countQuery = "SELECT COUNT(t) FROM Transaction t")
    Page<Long> findAllIds(Pageable pageable);

    @Query(value = "SELECT t.id FROM Transaction t WHERE t.status = :status ORDER BY t.submittedAt DESC",
           countQuery = "SELECT COUNT(t) FROM Transaction t WHERE t.status = :status")
    Page<Long> findIdsByStatus(@Param("status") Transaction.TransactionStatus status, Pageable pageable);

    @Query(value = "SELECT t.id FROM Transaction t WHERE t.submittedBy = :user ORDER BY t.submittedAt DESC",
           countQuery = "SELECT COUNT(t) FROM Transaction t WHERE t.submittedBy = :user")
    Page<Long> findIdsByUser(@Param("user") User user, Pageable pageable);

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

    /** All transactions submitted in a date range — used by history endpoint. */
    @Query("SELECT t FROM Transaction t " +
           "JOIN FETCH t.submittedBy u JOIN FETCH t.medicine m JOIN FETCH m.pharmaCompany " +
           "LEFT JOIN FETCH t.approvedBy " +
           "WHERE t.submittedAt >= :start AND t.submittedAt < :end " +
           "ORDER BY t.submittedAt DESC")
    List<Transaction> findBySubmittedAtBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** Transactions of a specific status submitted in a date range — used by history endpoint. */
    @Query("SELECT t FROM Transaction t " +
           "JOIN FETCH t.submittedBy u JOIN FETCH t.medicine m JOIN FETCH m.pharmaCompany " +
           "LEFT JOIN FETCH t.approvedBy " +
           "WHERE t.submittedAt >= :start AND t.submittedAt < :end AND t.status = :status " +
           "ORDER BY t.submittedAt DESC")
    List<Transaction> findBySubmittedAtBetweenAndStatus(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("status") Transaction.TransactionStatus status);

    @Query("SELECT t FROM Transaction t " +
           "JOIN FETCH t.submittedBy u JOIN FETCH t.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE t.status = :status AND t.approvedAt IS NOT NULL AND t.approvedAt < :endExclusive " +
           "ORDER BY u.fullName, m.name")
    List<Transaction> findApprovedUpTo(
            @Param("status") Transaction.TransactionStatus status,
            @Param("endExclusive") LocalDateTime endExclusive);

    /**
     * All non-rejected transactions submitted before endExclusive.
     * Mirrors how the Inventory table works: stock is deducted at submission time and
     * only added back on rejection — so PENDING + APPROVED txs both reduce stock.
     */
    @Query("SELECT t FROM Transaction t " +
           "JOIN FETCH t.submittedBy u JOIN FETCH t.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE t.status != :rejected AND t.submittedAt < :endExclusive " +
           "ORDER BY u.fullName, m.name")
    List<Transaction> findNonRejectedSubmittedUpTo(
            @Param("rejected") Transaction.TransactionStatus rejected,
            @Param("endExclusive") LocalDateTime endExclusive);

    /** Approved transactions approved on or after 'from' — used by backward historical reconstruction. */
    @Query("SELECT t FROM Transaction t " +
           "JOIN FETCH t.submittedBy u JOIN FETCH t.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE t.status = :status AND t.approvedAt IS NOT NULL AND t.approvedAt >= :from " +
           "ORDER BY u.fullName, m.name")
    List<Transaction> findApprovedFrom(
            @Param("status") Transaction.TransactionStatus status,
            @Param("from") LocalDateTime from);
}
