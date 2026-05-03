package com.pharma.inventory.repository;
import com.pharma.inventory.entity.Transaction;
import com.pharma.inventory.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
public interface TransactionRepository extends JpaRepository<Transaction,Long> {
    List<Transaction> findAllByOrderBySubmittedAtDesc();
    List<Transaction> findBySubmittedByOrderBySubmittedAtDesc(User user);
    void deleteBySubmittedById(Long userId);
    @Modifying
    @Query("UPDATE Transaction t SET t.approvedBy = null WHERE t.approvedBy.id = :userId")
    void nullifyApprovedBy(Long userId);
    @Query("SELECT t FROM Transaction t JOIN FETCH t.submittedBy u JOIN FETCH t.medicine m JOIN FETCH m.pharmaCompany WHERE t.status = :status AND t.approvedAt >= :start AND t.approvedAt < :end ORDER BY u.fullName, m.name")
    List<Transaction> findApprovedBetween(@org.springframework.data.repository.query.Param("status") Transaction.TransactionStatus status, @org.springframework.data.repository.query.Param("start") LocalDateTime start, @org.springframework.data.repository.query.Param("end") LocalDateTime end);
}
