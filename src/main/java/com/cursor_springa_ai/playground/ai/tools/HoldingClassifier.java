package com.cursor_springa_ai.playground.ai.tools;

import com.cursor_springa_ai.playground.analytics.model.EnrichedHoldingData;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Shared classification helpers used by {@link FlaggedHoldingsBuilder} and
 * {@link HoldingDetailsBuilder}. Contains only pure, stateless methods — safe
 * to call from any context.
 */
public final class HoldingClassifier {

    static final BigDecimal CORE_THRESHOLD = BigDecimal.valueOf(20);
    static final BigDecimal SIGNIFICANT_THRESHOLD = BigDecimal.valueOf(10);
    static final BigDecimal SUPPORTING_THRESHOLD = BigDecimal.valueOf(5);

    /**
     * Threshold above which a single holding's allocation is flagged as a concentration risk.
     * Intentionally kept equal to {@link #CORE_THRESHOLD} but as a separate constant so that
     * importance classification and concentration-risk detection can be tuned independently.
     */
    static final BigDecimal CONCENTRATION_THRESHOLD = BigDecimal.valueOf(20);

    static final List<String> RISK_PRIORITY = List.of(
            "HIGH_CONCENTRATION",
            "DEEP_CORRECTION",
            "HIGH_VALUATION",
            "PROFIT_BOOKING_ZONE",
            "SMALL_CAP_RISK"
    );

    /**
     * Null-safe descending comparator for {@link EnrichedHoldingData#allocationPercent()}.
     * Nulls are sorted last.
     */
    public static final Comparator<EnrichedHoldingData> BY_ALLOCATION_DESC =
            Comparator.comparing(EnrichedHoldingData::allocationPercent,
                    Comparator.nullsLast(Comparator.reverseOrder()));

    private HoldingClassifier() {
    }

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

    /**
     * Classifies risk severity based on the number of risk flags.
     * Returns NONE for no flags, LOW for one, MODERATE for two, HIGH for three or more.
     */
    static String classifyRiskSeverity(List<String> riskFlags) {
        int count = riskFlags.size();
        if (count >= 3) {
            return "HIGH";
        }
        if (count == 2) {
            return "MODERATE";
        }
        if (count == 1) {
            return "LOW";
        }
        return "NONE";
    }

    static String determinePrimaryRisk(List<String> riskFlags) {
        if (riskFlags.isEmpty()) {
            return null;
        }
        for (String flag : RISK_PRIORITY) {
            if (riskFlags.contains(flag)) {
                return flag;
            }
        }
        return riskFlags.getFirst();
    }
}
