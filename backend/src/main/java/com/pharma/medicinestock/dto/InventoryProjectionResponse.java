package com.pharma.medicinestock.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

/**
 * Trend-based projection of future weekly demand, grouped into exactly two lines —
 * "Total Oral" (every tablet strength, every pharma company) and "Total Injectable" (every
 * vial spec, every pharma company) — rather than per brand or per exact spec. Brand isn't a
 * substitution boundary (tablet strengths are interchangeable for orders; injections and
 * tablets are not), so it isn't a grouping dimension either.
 *
 * <p>Every line's quantity is always in mg — a merged line mixes specs with different
 * native-unit-to-mg ratios (e.g. a 10ml and a 5ml vial are both "1 vial" but not the same mg),
 * so there's no single meaningful native count for the regression to fit against. The frontend
 * derives an equivalent strip/vial count for display by dividing the mg series by whichever
 * reference spec the admin has chosen (e.g. "30mg strips", "8ml vials") — a scalar transform of
 * this one real series, not a second independently-fit one. {@code dosageForm} ("TABLET" or
 * "VIAL") tells the frontend which reference-spec options apply to a given line.
 *
 * <p>Revenue is deliberately never called "profit" — this app has no cost/purchase-price data
 * — and is computed from a blended price-per-mg (historical revenue ÷ historical mg), since a
 * merged line has no single native unit price either.
 *
 * <p>{@code regressionSlopeMg}/{@code regressionInterceptMg} are exposed so the frontend can
 * answer "given I currently have N left, when does it run out" purely client-side, against
 * whichever reference spec is selected, without a second request every time the admin types a
 * number. {@code forecastStartDate} is the calendar anchor those coefficients are relative to
 * (week index {@code historyWeeks} corresponds to this date).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryProjectionResponse {
    private List<String> historicalLabels;
    private List<String> forecastLabels;
    private String forecastStartDate;
    private int historyWeeks;
    private List<LineProjection> lines;
    private long totalForecastRevenue;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineProjection {
        private String name;
        private String dosageForm;
        private List<BigDecimal> historicalQuantityMg;
        private List<BigDecimal> forecastQuantityMg;
        private List<Long> forecastRevenue;
        private long totalForecastRevenue;
        private double regressionSlopeMg;
        private double regressionInterceptMg;
    }
}
