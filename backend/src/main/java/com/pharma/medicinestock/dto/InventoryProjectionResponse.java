package com.pharma.medicinestock.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

/**
 * Linear-regression stock/sales projection — past weekly quantity per medicine line (tablet
 * strengths combined per pharma company, since they're interchangeable for orders; vials kept
 * per spec) fitted and extrapolated into future weeks. Revenue is deliberately never called
 * "profit" — this app has no cost/purchase-price data, so it's price × projected quantity only.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryProjectionResponse {
    private List<String> historicalLabels;
    private List<String> forecastLabels;
    private List<LineProjection> lines;
    private BigDecimal totalForecastQuantity;
    private long totalForecastRevenue;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineProjection {
        private String name;
        private List<BigDecimal> historicalQuantity;
        private List<BigDecimal> forecastQuantity;
        private List<Long> forecastRevenue;
    }
}
