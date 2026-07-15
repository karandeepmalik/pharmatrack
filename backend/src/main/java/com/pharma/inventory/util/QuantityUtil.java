package com.pharma.inventory.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Quantities are tracked to exactly one decimal place. Any value with more precision (e.g. a
 * user submitting 1.25) is rounded — never rejected — to the nearest tenth (1.3) via HALF_UP.
 */
public final class QuantityUtil {

    private QuantityUtil() {}

    public static BigDecimal round(BigDecimal raw) {
        return raw == null ? null : raw.setScale(1, RoundingMode.HALF_UP);
    }
}
