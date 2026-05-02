package com.pharma.inventory.repository;
import com.pharma.inventory.entity.Medicine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface MedicineRepository extends JpaRepository<Medicine,Long> {
    List<Medicine> findByActiveTrue();
    List<Medicine> findByPharmaCompanyIdAndActiveTrue(Long pharmaId);
}
