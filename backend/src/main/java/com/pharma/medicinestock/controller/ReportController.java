package com.pharma.medicinestock.controller;

import com.pharma.medicinestock.dto.InventoryProjectionResponse;
import com.pharma.medicinestock.dto.ReportResponse;
import com.pharma.medicinestock.dto.SalesGraphResponse;
import com.pharma.medicinestock.dto.StockRunwayResponse;
import com.pharma.medicinestock.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/medicine-stock-by-user")
    public ResponseEntity<ReportResponse> medicineStockByUser() {
        return ResponseEntity.ok(reportService.medicineStockByUser());
    }

    @GetMapping("/medicine-stock-valuation")
    public ResponseEntity<ReportResponse> medicineStockValuation(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reportService.medicineStockValuation(date));
    }

    @GetMapping("/today-sales")
    public ResponseEntity<ReportResponse> todaySales(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "medicineId", required = false) Long medicineId) {
        return ResponseEntity.ok(reportService.todaySales(from, to, username, medicineId));
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

    /**
     * @param medicineStockType optional exact filter — {@code REGULAR_MEDICINE_STOCK} or
     *                          {@code ADMIN_MEDICINE_STOCK}; null/blank/{@code ALL} (default)
     *                          includes both, matching this app's existing optional-filter
     *                          convention (see {@code todaySales}'s {@code username} param).
     */
    @GetMapping("/sales-graph")
    public ResponseEntity<SalesGraphResponse> salesGraph(
            @RequestParam(value = "period", defaultValue = "daily") String period,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "medicineStockType", required = false) String medicineStockType) {
        return ResponseEntity.ok(reportService.salesGraph(period, from, to, medicineStockType));
    }

    /**
     * @param weeksAhead how many future weeks to project, clamped server-side to [1, 26]
     * @param cities     optional city filter (submitting user's {@code location}); omitted/empty
     *                   means "Overall" — every city combined
     */
    @GetMapping("/inventory-projection")
    public ResponseEntity<InventoryProjectionResponse> inventoryProjection(
            @RequestParam(value = "weeksAhead", required = false) Integer weeksAhead,
            @RequestParam(value = "cities", required = false) List<String> cities) {
        return ResponseEntity.ok(reportService.inventoryProjection(weeksAhead, cities));
    }

    /**
     * @param city      optional single-city filter; omitted/blank means every city combined
     * @param oralStock current oral stock on hand, in {@code oralUnit}; omitted/≤0 skips oral
     * @param oralUnit  {@code MG} or {@code STRIPS} (default STRIPS)
     * @param oralRefMg reference tablet strength in mg (20/30/50, default 30); only used when
     *                  {@code oralUnit} is STRIPS
     * @param vialStock current vial count on hand; omitted/≤0 skips injectable
     * @param vialRefMl reference vial volume in ml (8/10, default 8)
     */
    @GetMapping("/stock-runway")
    public ResponseEntity<StockRunwayResponse> stockRunway(
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "oralStock", required = false) BigDecimal oralStock,
            @RequestParam(value = "oralUnit", required = false, defaultValue = "STRIPS") String oralUnit,
            @RequestParam(value = "oralRefMg", required = false) Integer oralRefMg,
            @RequestParam(value = "vialStock", required = false) BigDecimal vialStock,
            @RequestParam(value = "vialRefMl", required = false) Integer vialRefMl) {
        return ResponseEntity.ok(reportService.stockRunway(city, oralStock, oralUnit, oralRefMg, vialStock, vialRefMl));
    }
}
