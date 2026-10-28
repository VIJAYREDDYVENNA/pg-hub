package com.hostelmanagement.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Helpers for currency arithmetic. Always BigDecimal with 2 decimal places. */
public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private Money() {
    }

    public static BigDecimal of(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(String value) {
        return of(new BigDecimal(value));
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
