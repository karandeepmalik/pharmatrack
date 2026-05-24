package com.pharma.inventory.repository;
import com.pharma.inventory.entity.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, Long> {

    void deleteByUserId(Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE InventoryAdjustment a SET a.adjustedBy = null WHERE a.adjustedBy.id = :userId")
    void nullifyAdjustedBy(@Param("userId") Long userId);

    @Query("SELECT a FROM InventoryAdjustment a " +
           "JOIN FETCH a.user u JOIN FETCH a.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE a.adjustedAt >= :start AND a.adjustedAt < :end " +
           "ORDER BY a.adjustedAt ASC")
    List<InventoryAdjustment> findByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT a FROM InventoryAdjustment a " +
           "JOIN FETCH a.user JOIN FETCH a.medicine " +
           "WHERE a.inTransit = true AND a.adjustedAt >= :cutoff")
    List<InventoryAdjustment> findActiveInTransitAdjustments(@Param("cutoff") LocalDateTime cutoff);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE InventoryAdjustment a SET a.inTransit = false WHERE a.inTransit = true AND a.adjustedAt < :cutoff")
    int expireOldInTransitAdjustments(@Param("cutoff") LocalDateTime cutoff);
}
