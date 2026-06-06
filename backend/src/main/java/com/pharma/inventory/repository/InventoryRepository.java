package com.pharma.inventory.repository;
import com.pharma.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory,Long> {

    Optional<Inventory> findByUserIdAndMedicineId(Long userId, Long medicineId);

    Optional<Inventory> findByUserIdAndMedicineIdAndInventoryType(
            Long userId, Long medicineId, Inventory.InventoryType inventoryType);

    /** Available inventory for a user — only REGULAR type (used by SubmitTransaction). */
    @Query("SELECT i FROM Inventory i JOIN FETCH i.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE i.user.id = :userId AND i.inventoryType = :type AND i.quantity > 0")
    List<Inventory> findAvailableByUserIdAndType(
            @Param("userId") Long userId,
            @Param("type") Inventory.InventoryType type);

    List<Inventory> findByUserId(Long userId);
    void deleteByUserId(Long userId);

    /** All non-zero inventory of a given type ordered by medicine then user — used by inventory-by-user report (ADMIN). */
    @Query("SELECT i FROM Inventory i JOIN FETCH i.user u JOIN FETCH i.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE i.quantity > 0 AND i.inventoryType = :type " +
           "ORDER BY m.name, m.specification, u.fullName")
    List<Inventory> findAllNonZeroOrderByMedicineAndUser(@Param("type") Inventory.InventoryType type);

    /**
     * REGULAR stock ordered by medicine then user — includes rows where inventoryType IS NULL.
     * The InventoryTypeConverter maps NULL→REGULAR_MEDICINE_STOCK at the Java level,
     * but JPQL WHERE equality cannot match SQL NULL, so we add OR IS NULL explicitly.
     */
    @Query("SELECT i FROM Inventory i JOIN FETCH i.user u JOIN FETCH i.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE i.quantity > 0 AND (i.inventoryType = :type OR i.inventoryType IS NULL) " +
           "ORDER BY m.name, m.specification, u.fullName")
    List<Inventory> findAllNonZeroRegularOrderByMedicineAndUser(@Param("type") Inventory.InventoryType type);

    /** All non-zero inventory of a given type — used by valuation report (ADMIN). */
    @Query("SELECT i FROM Inventory i JOIN FETCH i.user u JOIN FETCH i.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE i.quantity > 0 AND i.inventoryType = :type " +
           "ORDER BY m.name, m.specification")
    List<Inventory> findAllNonZeroForValuation(@Param("type") Inventory.InventoryType type);

    /**
     * REGULAR stock for valuation — includes rows where inventoryType IS NULL.
     * Same NULL-inclusion rationale as findAllNonZeroRegularOrderByMedicineAndUser.
     */
    @Query("SELECT i FROM Inventory i JOIN FETCH i.user u JOIN FETCH i.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE i.quantity > 0 AND (i.inventoryType = :type OR i.inventoryType IS NULL) " +
           "ORDER BY m.name, m.specification")
    List<Inventory> findAllNonZeroRegularForValuation(@Param("type") Inventory.InventoryType type);

    /** All non-zero inventory of a given type — used by daily report (ADMIN_STOCK section). */
    @Query("SELECT i FROM Inventory i JOIN FETCH i.user u JOIN FETCH i.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE i.quantity > 0 AND i.inventoryType = :type " +
           "ORDER BY m.name, m.specification, u.fullName")
    List<Inventory> findAllNonZeroByInventoryType(@Param("type") Inventory.InventoryType type);

    /**
     * REGULAR stock for daily report — includes rows where inventoryType IS NULL.
     * Same NULL-inclusion rationale as findAllNonZeroRegularOrderByMedicineAndUser.
     */
    @Query("SELECT i FROM Inventory i JOIN FETCH i.user u JOIN FETCH i.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE i.quantity > 0 AND (i.inventoryType = :type OR i.inventoryType IS NULL) " +
           "ORDER BY m.name, m.specification, u.fullName")
    List<Inventory> findAllNonZeroRegularByInventoryType(@Param("type") Inventory.InventoryType type);
}
