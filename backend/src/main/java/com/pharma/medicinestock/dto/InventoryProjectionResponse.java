package com.pharma.medicinestock.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

/**
 * Linear-regression projection of future weekly demand, grouped at the injection-vial vs
 * oral-tablet level (every vial spec combines into one "Injection Vial" line per pharma
 * company, every tablet strength into one "Oral Tablet" line) rather than per exact spec,
 * since the two dosage forms are what's operationally interchangeable/substitutable. Each
 * line exposes its quantity in both its native count unit ({@code unit}: vials or strips) and
 * an mg-equivalent, so the frontend can toggle between them without a second round trip —
 * these are two independently-fitted regressions, not one derived from the other, since a
 * merged line mixes specs with different native-unit-to-mg ratios (e.g. a 10ml and a 5ml vial
 * are both "1 vial" but not the same mg). Revenue is deliberately never called "profit" — this
 * app has no cost/purchase-price data — and is always computed from the native-unit forecast,
 * since price is defined per vial/strip, not per mg.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryProjectionResponse {
    private List<String> historicalLabels;
    private List<String> forecastLabels;
    private List<LineProjection> lines;
    private long totalForecastRevenue;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineProjection {
        private String name;
        private String unit;
        private List<BigDecimal> historicalQuantity;
        private List<BigDecimal> forecastQuantity;
        private List<BigDecimal> historicalQuantityMg;
        private List<BigDecimal> forecastQuantityMg;
        private List<Long> forecastRevenue;
        private long totalForecastRevenue;
    }
}
