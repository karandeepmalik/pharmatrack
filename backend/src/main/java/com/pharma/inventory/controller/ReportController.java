package com.pharma.inventory.controller;

import com.pharma.inventory.dto.ReportResponse;
import com.pharma.inventory.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

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
    public ResponseEntity<ReportResponse> inventoryValuation(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reportService.inventoryValuation(date));
    }

    @GetMapping("/today-sales")
    public ResponseEntity<ReportResponse> todaySales(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.todaySales(from, to));
    }

    /**
     * Daily report for the given date (optional — defaults to today in IST).
     *
     * @param date optional date in ISO format YYYY-MM-DD
     */
    @GetMapping("/daily")
    public ResponseEntity<ReportResponse> dailyReport(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reportService.dailyReport(date));
    }
}
