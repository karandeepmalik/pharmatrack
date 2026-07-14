package com.pharma.inventory.repository;
import com.pharma.inventory.entity.Inventory;
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
           "WHERE a.inTransit = true")
    List<InventoryAdjustment> findAllActiveInTransit();

    /**
     * Active in-transit ADD adjustments for one specific user/medicine/type bucket — used to
     * compute settled (actually dispatchable) stock by excluding not-yet-arrived quantity.
     */
    @Query("SELECT a FROM InventoryAdjustment a " +
           "WHERE a.user.id = :userId AND a.medicine.id = :medicineId AND a.inventoryType = :type " +
           "AND a.inTransit = true AND a.adjustmentType = 'ADD'")
    List<InventoryAdjustment> findActiveInTransitFor(
            @Param("userId") Long userId,
            @Param("medicineId") Long medicineId,
            @Param("type") Inventory.InventoryType type);

    @Query("SELECT a FROM InventoryAdjustment a " +
           "JOIN FETCH a.user u JOIN FETCH a.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE a.adjustedAt < :endExclusive " +
           "ORDER BY m.name, m.specification, u.fullName")
    List<InventoryAdjustment> findAllUpTo(@Param("endExclusive") LocalDateTime endExclusive);

    /** Adjustments made on or after 'from' — used by backward historical reconstruction. */
    @Query("SELECT a FROM InventoryAdjustment a " +
           "JOIN FETCH a.user u JOIN FETCH a.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE a.adjustedAt >= :from " +
           "ORDER BY m.name, m.specification, u.fullName")
    List<InventoryAdjustment> findAllFrom(@Param("from") LocalDateTime from);

    @Query("SELECT a FROM InventoryAdjustment a " +
           "JOIN FETCH a.user u JOIN FETCH a.medicine m JOIN FETCH m.pharmaCompany " +
           "LEFT JOIN FETCH a.adjustedBy " +
           "WHERE a.adjustedAt >= :start AND a.adjustedAt < :end " +
           "ORDER BY a.adjustedAt DESC")
    List<InventoryAdjustment> findWithDetailsBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
