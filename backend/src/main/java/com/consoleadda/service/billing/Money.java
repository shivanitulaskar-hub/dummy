package com.consoleadda.service.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money rounding policy (see docs/ARCHITECTURE.md §5.6): every monetary result is rounded to
 * 2 decimals with HALF_UP as soon as it is produced. Never use float/double for money.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
    public static final BigDecimal HUNDRED = new BigDecimal("100");

    private Money() {
    }

    public static BigDecimal of(String value) {
        return round(new BigDecimal(value));
    }

    public static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
