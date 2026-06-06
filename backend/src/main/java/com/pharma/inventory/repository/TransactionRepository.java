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
    List<Transaction> findAllByOrderBySubmittedAtDesc();
    List<Transaction> findBySubmittedByOrderBySubmittedAtDesc(User user);
    void deleteBySubmittedById(Long userId);

    @Modifying
    @Query("UPDATE Transaction t SET t.approvedBy = null WHERE t.approvedBy.id = :userId")
    void nullifyApprovedBy(Long userId);

    /** Approved transactions whose dispatch date (submittedAt) falls on a given day — used by daily report. */
    @Query("SELECT t FROM Transaction t JOIN FETCH t.submittedBy u JOIN FETCH t.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE t.status = :status AND t.submittedAt >= :start AND t.submittedAt < :end " +
           "ORDER BY u.fullName, m.name")
    List<Transaction> findApprovedBetween(
            @Param("status") Transaction.TransactionStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** All transactions submitted in a date range — used by history endpoint. */
    @Query("SELECT t FROM Transaction t JOIN FETCH t.submittedBy u JOIN FETCH t.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE t.submittedAt >= :start AND t.submittedAt < :end " +
           "ORDER BY t.submittedAt DESC")
    List<Transaction> findBySubmittedAtBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** Transactions of a specific status submitted in a date range — used by history endpoint. */
    @Query("SELECT t FROM Transaction t JOIN FETCH t.submittedBy u JOIN FETCH t.medicine m JOIN FETCH m.pharmaCompany " +
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
