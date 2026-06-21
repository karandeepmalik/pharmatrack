package com.pharma.inventory.repository;
import com.pharma.inventory.entity.Transaction;
import com.pharma.inventory.entity.User;
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

    /** Approved transactions approved on or after 'from' — used by backward historical reconstruction. */
    @Query("SELECT t FROM Transaction t " +
           "JOIN FETCH t.submittedBy u JOIN FETCH t.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE t.status = :status AND t.approvedAt IS NOT NULL AND t.approvedAt >= :from " +
           "ORDER BY u.fullName, m.name")
    List<Transaction> findApprovedFrom(
            @Param("status") Transaction.TransactionStatus status,
            @Param("from") LocalDateTime from);
}
