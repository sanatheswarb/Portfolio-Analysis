package com.cursor_springa_ai.playground.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared null-safe {@link BigDecimal} helpers used across analytics, importers, and services.
 */
public final class BigDecimalUtils {

    private BigDecimalUtils() {
    }

    /**
     * Returns the given value, or {@link BigDecimal#ZERO} when {@code null}.
     */
    public static BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * Rounds the value to 2 decimal places (HALF_UP), returning {@link BigDecimal#ZERO}
     * when {@code null}.
     */
    public static BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
