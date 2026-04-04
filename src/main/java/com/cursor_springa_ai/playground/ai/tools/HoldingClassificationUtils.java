package com.cursor_springa_ai.playground.ai.tools;

import java.math.BigDecimal;

/**
 * Shared classification helpers used by both {@link FlaggedHoldingsBuilder}
 * and {@link HoldingDetailsBuilder} to ensure consistent AI-facing labels.
 */
final class HoldingClassificationUtils {

    private static final BigDecimal CORE_THRESHOLD = BigDecimal.valueOf(20);
    private static final BigDecimal SIGNIFICANT_THRESHOLD = BigDecimal.valueOf(10);
    private static final BigDecimal SUPPORTING_THRESHOLD = BigDecimal.valueOf(5);

    private HoldingClassificationUtils() {
    }

    /**
     * Classifies a holding's importance based on its allocation percentage.
     *
     * @return CORE, SIGNIFICANT, SUPPORTING, or MINOR
     */
    static String classifyImportance(BigDecimal allocationPercent) {
        if (allocationPercent == null) {
            return "MINOR";
        }
        if (allocationPercent.compareTo(CORE_THRESHOLD) > 0) {
            return "CORE";
        }
        if (allocationPercent.compareTo(SIGNIFICANT_THRESHOLD) > 0) {
            return "SIGNIFICANT";
        }
        if (allocationPercent.compareTo(SUPPORTING_THRESHOLD) > 0) {
            return "SUPPORTING";
        }
        return "MINOR";
    }

    /**
     * Classifies a holding's performance status based on profit percentage.
     *
     * @return PROFIT, LOSS, BREAKEVEN, or {@code null} when input is {@code null}
     */
    static String classifyPerformance(BigDecimal profitPercent) {
        if (profitPercent == null) {
            return null;
        }
        int comparison = profitPercent.compareTo(BigDecimal.ZERO);
        if (comparison > 0) {
            return "PROFIT";
        }
        if (comparison < 0) {
            return "LOSS";
        }
        return "BREAKEVEN";
    }
}
