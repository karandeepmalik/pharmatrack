package com.pharma.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesGraphResponse {
    private String period;
    private List<DataPoint> dataPoints;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpecBreakdown {
        private String specName;
        private BigDecimal quantity;
        private long value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        private String label;
        private BigDecimal quantity;
        private long value;
        private List<SpecBreakdown> specs;
    }
}
