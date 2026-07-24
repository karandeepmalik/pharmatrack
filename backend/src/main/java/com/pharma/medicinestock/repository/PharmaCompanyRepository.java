package com.pharma.medicinestock.repository;
import com.pharma.medicinestock.entity.PharmaCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PharmaCompanyRepository extends JpaRepository<PharmaCompany,Long> {
    List<PharmaCompany> findByActiveTrue();
}
