package com.pharma.medicinestock.repository;
import com.pharma.medicinestock.entity.MedicineStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface MedicineStockRepository extends JpaRepository<MedicineStock,Long> {

    Optional<MedicineStock> findByUserIdAndMedicineIdAndMedicineStockType(
            Long userId, Long medicineId, MedicineStock.MedicineStockType medicineStockType);

    /** Available medicine stock for a user — only REGULAR type (used by SubmitTransaction). */
    @Query("SELECT i FROM MedicineStock i JOIN FETCH i.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE i.user.id = :userId AND i.medicineStockType = :type AND i.quantity > 0")
    List<MedicineStock> findAvailableByUserIdAndType(
            @Param("userId") Long userId,
            @Param("type") MedicineStock.MedicineStockType type);

    void deleteByUserId(Long userId);

    /** All non-zero medicine stock of a given type ordered by medicine then user — used by the medicine-stock-by-user report (ADMIN). */
    @Query("SELECT i FROM MedicineStock i JOIN FETCH i.user u JOIN FETCH i.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE i.quantity > 0 AND i.medicineStockType = :type " +
           "ORDER BY m.name, m.specification, u.fullName")
    List<MedicineStock> findAllNonZeroOrderByMedicineAndUser(@Param("type") MedicineStock.MedicineStockType type);

    /**
     * REGULAR stock ordered by medicine then user — includes rows where medicineStockType IS NULL.
     * The MedicineStockTypeConverter maps NULL→REGULAR_MEDICINE_STOCK at the Java level,
     * but JPQL WHERE equality cannot match SQL NULL, so we add OR IS NULL explicitly.
     */
    @Query("SELECT i FROM MedicineStock i JOIN FETCH i.user u JOIN FETCH i.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE i.quantity > 0 AND (i.medicineStockType = :type OR i.medicineStockType IS NULL) " +
           "ORDER BY m.name, m.specification, u.fullName")
    List<MedicineStock> findAllNonZeroRegularOrderByMedicineAndUser(@Param("type") MedicineStock.MedicineStockType type);

    /**
     * REGULAR stock for valuation — includes rows where medicineStockType IS NULL.
     * Same NULL-inclusion rationale as findAllNonZeroRegularOrderByMedicineAndUser.
     */
    @Query("SELECT i FROM MedicineStock i JOIN FETCH i.user u JOIN FETCH i.medicine m JOIN FETCH m.pharmaCompany " +
           "WHERE i.quantity > 0 AND (i.medicineStockType = :type OR i.medicineStockType IS NULL) " +
           "ORDER BY m.name, m.specification")
    List<MedicineStock> findAllNonZeroRegularForValuation(@Param("type") MedicineStock.MedicineStockType type);
}
