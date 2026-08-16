package com.pharma.medicinestock.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MoneyMath")
class MoneyMathTest {

    @Test
    @DisplayName("multiplies a whole-unit price by a whole quantity")
    void wholeQuantity() {
        assertThat(MoneyMath.amount(100, new BigDecimal("5"))).isEqualTo(500L);
    }

    @Test
    @DisplayName("multiplies a price by a fractional quantity and rounds to the nearest rupee")
    void fractionalQuantityRounds() {
        assertThat(MoneyMath.amount(150, new BigDecimal("2.5"))).isEqualTo(375L);
    }

    @Test
    @DisplayName("rounds HALF_UP, not banker's rounding, at exactly .5")
    void roundsHalfUpNotBankers() {
        // 333 * 0.5 = 166.5 -> HALF_UP rounds to 167, not 166 (banker's rounding would give 166)
        assertThat(MoneyMath.amount(333, new BigDecimal("0.5"))).isEqualTo(167L);
    }

    @Test
    @DisplayName("rounds down when the fractional part is below .5")
    void roundsDown() {
        // 100 * 1.24 = 124.0 exactly, no rounding needed
        assertThat(MoneyMath.amount(100, new BigDecimal("1.24"))).isEqualTo(124L);
    }

    @Test
    @DisplayName("returns zero for zero quantity")
    void zeroQuantity() {
        assertThat(MoneyMath.amount(999, BigDecimal.ZERO)).isEqualTo(0L);
    }

    @Test
    @DisplayName("returns zero for zero price")
    void zeroPrice() {
        assertThat(MoneyMath.amount(0, new BigDecimal("10.0"))).isEqualTo(0L);
    }
}
