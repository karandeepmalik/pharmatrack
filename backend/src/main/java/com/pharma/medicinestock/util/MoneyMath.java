package com.pharma.medicinestock.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money is always a whole number of rupees. Every price × quantity calculation across every
 * report rounds HALF_UP to the nearest rupee — this is the single shared implementation of
 * that rounding, rather than each call site re-implementing it.
 */
public final class MoneyMath {

    private MoneyMath() {}

    public static long amount(int unitPrice, BigDecimal quantity) {
        return BigDecimal.valueOf(unitPrice).multiply(quantity).setScale(0, RoundingMode.HALF_UP).longValue();
    }
}
