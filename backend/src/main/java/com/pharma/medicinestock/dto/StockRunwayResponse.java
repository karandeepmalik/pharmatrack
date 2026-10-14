package com.pharma.medicinestock.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

/**
 * "Given I currently have N left, how long does it last and when does it run out" — for a
 * single city (or every city combined), against the same mg-regression {@link ReportService}
 * fits for the Future Inventory Projections report. One {@link RunwayLine} per dosage form the
 * admin entered a stock figure for (Total Oral and/or Total Injectable); a type with no entered
 * stock is simply absent from {@code lines}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockRunwayResponse {
    private List<RunwayLine> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunwayLine {
        private String name;
        private String unit;
        private BigDecimal currentStock;
        private boolean insufficientData;
        private String stockOutDate;
        private List<String> dateLabels;
        private List<BigDecimal> remainingQuantity;
    }
}
