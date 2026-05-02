package com.pharma.inventory.repository;
import com.pharma.inventory.entity.PharmaCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PharmaCompanyRepository extends JpaRepository<PharmaCompany,Long> {
    List<PharmaCompany> findByActiveTrue();
}
