package com.pharma.inventory.repository;
import com.pharma.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
public interface InventoryRepository extends JpaRepository<Inventory,Long> {
    Optional<Inventory> findByUserIdAndMedicineId(Long userId, Long medicineId);
    @Query("SELECT i FROM Inventory i JOIN FETCH i.medicine m JOIN FETCH m.pharmaCompany WHERE i.user.id=:userId AND i.quantity>0")
    List<Inventory> findAvailableByUserId(Long userId);
    List<Inventory> findByUserId(Long userId);
    void deleteByUserId(Long userId);
    @Query("SELECT i FROM Inventory i JOIN FETCH i.user u JOIN FETCH i.medicine m JOIN FETCH m.pharmaCompany WHERE i.quantity > 0 ORDER BY m.name, m.specification, u.fullName")
    List<Inventory> findAllNonZeroOrderByMedicineAndUser();
    @Query("SELECT i FROM Inventory i JOIN FETCH i.user u JOIN FETCH i.medicine m JOIN FETCH m.pharmaCompany WHERE i.quantity > 0 AND u.role <> 'ADMIN' ORDER BY m.name, m.specification")
    List<Inventory> findAllNonZeroForValuation();
    @Query("SELECT i FROM Inventory i JOIN FETCH i.user u JOIN FETCH i.medicine m JOIN FETCH m.pharmaCompany WHERE u.role = 'ADMIN' AND i.lastUpdated >= :start AND i.lastUpdated < :end AND i.lastNote IS NOT NULL AND i.lastNote <> ''")
    List<Inventory> findAdminModificationsToday(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
