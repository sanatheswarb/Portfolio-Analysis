package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.ai.FlaggedHoldingDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the AI-facing flagged holdings payload from enriched holding data.
 * Contains all derivation logic so the tool layer remains a pure mapping class.
 */
public class FlaggedHoldingsBuilder {

    private static final BigDecimal CORE_THRESHOLD = BigDecimal.valueOf(20);
    private static final BigDecimal SIGNIFICANT_THRESHOLD = BigDecimal.valueOf(10);
    private static final BigDecimal SUPPORTING_THRESHOLD = BigDecimal.valueOf(5);
    private static final BigDecimal ALLOCATION_FILTER_THRESHOLD = BigDecimal.valueOf(10);
    private static final BigDecimal NEAR_HIGH_THRESHOLD = BigDecimal.valueOf(-10);
    private static final BigDecimal DEEP_CORRECTION_THRESHOLD = BigDecimal.valueOf(-25);
    private static final int MAX_FLAGGED_HOLDINGS = 5;

    public List<FlaggedHoldingDto> build(PortfolioReasoningContext context) {
        return context.enrichedHoldings().stream()
                .filter(this::requiresAttention)
                .sorted((left, right) -> compareByAllocationDesc(right, left))
                .limit(MAX_FLAGGED_HOLDINGS)
                .map(this::toFlaggedHoldingDto)
                .toList();
    }

    private boolean requiresAttention(EnrichedHoldingData holding) {
        return isSignificantAllocation(holding) || hasRiskFlags(holding);
    }

    private boolean isSignificantAllocation(EnrichedHoldingData holding) {
        return holding.allocationPercent() != null
                && holding.allocationPercent().compareTo(ALLOCATION_FILTER_THRESHOLD) > 0;
    }

    private boolean hasRiskFlags(EnrichedHoldingData holding) {
        return holding.riskFlags() != null && !holding.riskFlags().isEmpty();
    }

    private FlaggedHoldingDto toFlaggedHoldingDto(EnrichedHoldingData holding) {
        List<String> riskFlags = holding.riskFlags() != null ? holding.riskFlags() : List.of();
        return new FlaggedHoldingDto(
                holding.symbol(),
                holding.allocationPercent(),
                classifyImportance(holding.allocationPercent()),
                classifyPerformance(holding.profitPercent()),
                holding.valuationFlag(),
                holding.momentumScore(),
                holding.distanceFromHigh(),
                holding.marketCapType(),
                classifyRiskSeverity(riskFlags),
                determinePrimaryConcern(riskFlags),
                riskFlags,
                buildAttentionReasons(holding)
        );
    }

    private String classifyImportance(BigDecimal allocationPercent) {
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

    private String classifyPerformance(BigDecimal profitPercent) {
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

    private String classifyRiskSeverity(List<String> riskFlags) {
        if (riskFlags.isEmpty()) {
            return "LOW";
        }
        int count = riskFlags.size();
        if (count >= 3) {
            return "HIGH";
        }
        if (count == 2) {
            return "MODERATE";
        }
        return "LOW";
    }

    private String determinePrimaryConcern(List<String> riskFlags) {
        if (riskFlags.isEmpty()) {
            return null;
        }
        List<String> priority = List.of(
                "HIGH_CONCENTRATION",
                "DEEP_CORRECTION",
                "HIGH_VALUATION",
                "PROFIT_BOOKING_ZONE",
                "SMALL_CAP_RISK"
        );
        for (String flag : priority) {
            if (riskFlags.contains(flag)) {
                return flag;
            }
        }
        return riskFlags.getFirst();
    }

    private List<String> buildAttentionReasons(EnrichedHoldingData holding) {
        List<String> reasons = new ArrayList<>();

        if (holding.allocationPercent() != null
                && holding.allocationPercent().compareTo(CORE_THRESHOLD) > 0) {
            reasons.add("Largest portfolio allocation");
        }

        if ("OVERVALUED".equals(holding.valuationFlag())) {
            reasons.add("Valuation above sector average");
        }

        if (holding.distanceFromHigh() != null
                && holding.distanceFromHigh().compareTo(NEAR_HIGH_THRESHOLD) > 0) {
            reasons.add("Trading near 52 week high");
        }

        if (holding.distanceFromHigh() != null
                && holding.distanceFromHigh().compareTo(DEEP_CORRECTION_THRESHOLD) < 0) {
            reasons.add("Trading well below 52 week high");
        }

        if (holding.profitPercent() != null
                && holding.profitPercent().compareTo(BigDecimal.ZERO) < 0) {
            reasons.add("Position is currently at a loss");
        }

        if ("smallcap".equalsIgnoreCase(holding.marketCapType())) {
            reasons.add("Small cap increases portfolio volatility");
        }

        return List.copyOf(reasons);
    }

    private int compareByAllocationDesc(EnrichedHoldingData left, EnrichedHoldingData right) {
        if (left.allocationPercent() == null && right.allocationPercent() == null) {
            return 0;
        }
        if (left.allocationPercent() == null) {
            return -1;
        }
        if (right.allocationPercent() == null) {
            return 1;
        }
        return left.allocationPercent().compareTo(right.allocationPercent());
    }
}
