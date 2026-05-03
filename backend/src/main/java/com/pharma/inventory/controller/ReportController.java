package com.pharma.inventory.controller;

import com.pharma.inventory.dto.ReportResponse;
import com.pharma.inventory.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/inventory-by-user")
    public ResponseEntity<ReportResponse> inventoryByUser() {
        return ResponseEntity.ok(reportService.inventoryByUser());
    }

    @GetMapping("/inventory-valuation")
    public ResponseEntity<ReportResponse> inventoryValuation() {
        return ResponseEntity.ok(reportService.inventoryValuation());
    }

    @GetMapping("/today-sales")
    public ResponseEntity<ReportResponse> todaySales() {
        return ResponseEntity.ok(reportService.todaySales());
    }
}
