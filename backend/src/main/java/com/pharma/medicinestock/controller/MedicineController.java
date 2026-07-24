package com.pharma.medicinestock.controller;
import com.pharma.medicinestock.dto.CreateMedicineRequest;
import com.pharma.medicinestock.dto.CreatePharmaCompanyRequest;
import com.pharma.medicinestock.dto.MedicineResponse;
import com.pharma.medicinestock.dto.PharmaCompanyResponse;
import com.pharma.medicinestock.service.MedicineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/medicines") @RequiredArgsConstructor
public class MedicineController {
    private final MedicineService medicineService;

    @GetMapping @PreAuthorize("isAuthenticated()")
    public List<MedicineResponse> getAll() { return medicineService.getAll(); }

    @GetMapping("/companies") @PreAuthorize("isAuthenticated()")
    public List<PharmaCompanyResponse> getCompanies() { return medicineService.getAllCompanies(); }

    @PostMapping("/companies") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PharmaCompanyResponse> createCompany(@Valid @RequestBody CreatePharmaCompanyRequest req) {
        return ResponseEntity.ok(medicineService.createCompany(req));
    }

    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MedicineResponse> createMedicine(@Valid @RequestBody CreateMedicineRequest req) {
        return ResponseEntity.ok(medicineService.createMedicine(req));
    }
}
