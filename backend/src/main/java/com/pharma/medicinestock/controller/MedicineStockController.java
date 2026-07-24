package com.pharma.medicinestock.controller;
import com.pharma.medicinestock.dto.*;
import com.pharma.medicinestock.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
@RestController @RequestMapping("/api/medicine-stock") @RequiredArgsConstructor
public class MedicineStockController {
    private final MedicineStockService medicineStockService;
    private final UserService userService;

    @GetMapping("/available")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<MedicineStockResponse>> getAvailable(@AuthenticationPrincipal UserDetails ud) {
        Long uid = userService.getByUsername(ud.getUsername()).getId();
        return ResponseEntity.ok(medicineStockService.getAvailableForUser(uid));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MedicineStockResponse>> getAll() {
        return ResponseEntity.ok(medicineStockService.getAll());
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MedicineStockResponse> adjust(
            @Valid @RequestBody AdjustMedicineStockRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(medicineStockService.adjustMedicineStock(req, ud.getUsername()));
    }

    @GetMapping("/adjustments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MedicineStockAdjustmentResponse>> getAdjustments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(medicineStockService.getAdjustments(from, to));
    }

    @DeleteMapping("/adjustments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAdjustment(@PathVariable Long id) {
        medicineStockService.deleteAdjustment(id);
        return ResponseEntity.noContent().build();
    }
}
