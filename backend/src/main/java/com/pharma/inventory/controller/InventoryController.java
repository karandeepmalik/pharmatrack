package com.pharma.inventory.controller;
import com.pharma.inventory.dto.*;
import com.pharma.inventory.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/inventory") @RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;
    private final UserService userService;

    @GetMapping("/available")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<InventoryResponse>> getAvailable(@AuthenticationPrincipal UserDetails ud) {
        Long uid = userService.getByUsername(ud.getUsername()).getId();
        return ResponseEntity.ok(inventoryService.getAvailableForUser(uid));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<InventoryResponse>> getAll() {
        return ResponseEntity.ok(inventoryService.getAll());
    }

    @GetMapping("/system")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<InventoryResponse>> getSystemInventory() {
        return ResponseEntity.ok(inventoryService.getSystemInventory());
    }

    @PostMapping("/system")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InventoryResponse> addSystemInventory(@Valid @RequestBody SystemInventoryRequest req) {
        return ResponseEntity.ok(inventoryService.addSystemInventory(req));
    }

    @PostMapping("/allocate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InventoryResponse> allocate(@Valid @RequestBody InventoryRequest req) {
        return ResponseEntity.ok(inventoryService.allocateToUser(req));
    }
}
