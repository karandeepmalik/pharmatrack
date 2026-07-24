package com.pharma.inventory.controller;
import com.pharma.inventory.dto.CreateMedicineRequest;
import com.pharma.inventory.dto.CreatePharmaCompanyRequest;
import com.pharma.inventory.dto.MedicineResponse;
import com.pharma.inventory.dto.PharmaCompanyResponse;
import com.pharma.inventory.service.MedicineService;
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
