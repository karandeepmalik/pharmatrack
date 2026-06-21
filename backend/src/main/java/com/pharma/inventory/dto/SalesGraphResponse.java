package com.pharma.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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
        private int quantity;
        private long value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        private String label;
        private int quantity;
        private long value;
        private List<SpecBreakdown> specs;
    }
}
