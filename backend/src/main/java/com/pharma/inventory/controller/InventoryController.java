package com.pharma.inventory.controller;
import com.pharma.inventory.dto.*;
import com.pharma.inventory.service.*;
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
@RestController @RequestMapping("/api/inventory") @RequiredArgsConstructor
public class InventoryController {
    private final MedicineStockService medicineStockService;
    private final UserService userService;

    @GetMapping("/available")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<InventoryResponse>> getAvailable(@AuthenticationPrincipal UserDetails ud) {
        Long uid = userService.getByUsername(ud.getUsername()).getId();
        return ResponseEntity.ok(medicineStockService.getAvailableForUser(uid));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<InventoryResponse>> getAll() {
        return ResponseEntity.ok(medicineStockService.getAll());
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InventoryResponse> adjust(
            @Valid @RequestBody AdjustInventoryRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(medicineStockService.adjustInventory(req, ud.getUsername()));
    }

    @GetMapping("/adjustments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<InventoryAdjustmentResponse>> getAdjustments(
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
