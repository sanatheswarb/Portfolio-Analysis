package com.cursor_springa_ai.playground.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Stateless arithmetic helpers for {@link BigDecimal} values used across
 * the analytics and persistence layers.
 */
public final class BigDecimalUtils {

    private BigDecimalUtils() {
    }

    /**
     * Scales a non-null {@code value} to two decimal places using {@link RoundingMode#HALF_UP}.
     * Returns {@link BigDecimal#ZERO} when {@code value} is {@code null}.
     *
     * @param value the value to scale; may be {@code null}
     * @return {@link BigDecimal#ZERO} if {@code value} is {@code null}; otherwise a non-null,
     *         two-decimal-place value
     */
    public static BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
