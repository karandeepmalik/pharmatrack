package com.pharma.inventory.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QuantityUtil")
class QuantityUtilTest {

    @Test
    @DisplayName("returns null for null input")
    void nullInputReturnsNull() {
        assertThat(QuantityUtil.round(null)).isNull();
    }

    @Test
    @DisplayName("leaves an already-1-decimal value unchanged")
    void leavesOneDecimalUnchanged() {
        assertThat(QuantityUtil.round(new BigDecimal("1.5")))
                .isEqualByComparingTo("1.5");
    }

    @Test
    @DisplayName("rounds a whole number up to scale 1 (10 -> 10.0)")
    void wholeNumberGetsScaleOne() {
        BigDecimal result = QuantityUtil.round(new BigDecimal("10"));
        assertThat(result).isEqualByComparingTo("10.0");
        assertThat(result.scale()).isEqualTo(1);
    }

    @Test
    @DisplayName("rounds 1.25 up to 1.3 (HALF_UP)")
    void roundsHalfUp() {
        assertThat(QuantityUtil.round(new BigDecimal("1.25")))
                .isEqualByComparingTo("1.3");
    }

    @Test
    @DisplayName("rounds 1.24 down to 1.2")
    void roundsDown() {
        assertThat(QuantityUtil.round(new BigDecimal("1.24")))
                .isEqualByComparingTo("1.2");
    }

    @Test
    @DisplayName("rounds 1.15 up to 1.2 (HALF_UP, not banker's rounding)")
    void roundsHalfUpNotBankers() {
        assertThat(QuantityUtil.round(new BigDecimal("1.15")))
                .isEqualByComparingTo("1.2");
    }

    @Test
    @DisplayName("rounds a very precise value to 1 decimal")
    void roundsManyDecimals() {
        assertThat(QuantityUtil.round(new BigDecimal("1.23456")))
                .isEqualByComparingTo("1.2");
    }

    @Test
    @DisplayName("rounds 0.05 up to 0.1")
    void roundsSmallValueUp() {
        assertThat(QuantityUtil.round(new BigDecimal("0.05")))
                .isEqualByComparingTo("0.1");
    }
}
