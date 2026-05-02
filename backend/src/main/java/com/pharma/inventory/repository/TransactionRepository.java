package com.pharma.inventory.repository;
import com.pharma.inventory.entity.Transaction;
import com.pharma.inventory.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TransactionRepository extends JpaRepository<Transaction,Long> {
    List<Transaction> findAllByOrderBySubmittedAtDesc();
    List<Transaction> findBySubmittedByOrderBySubmittedAtDesc(User user);
}
