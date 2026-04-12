package com.cursor_springa_ai.playground.ai.tools;

import com.cursor_springa_ai.playground.analytics.model.EnrichedHoldingData;
import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.ai.dto.FlaggedHoldingDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the AI-facing flagged holdings payload from enriched holding data.
 * Contains all derivation logic so the tool layer remains a pure mapping class.
 */
public class FlaggedHoldingsBuilder {

    private static final BigDecimal ALLOCATION_FILTER_THRESHOLD = BigDecimal.valueOf(10);
    private static final BigDecimal NEAR_HIGH_THRESHOLD = BigDecimal.valueOf(-10);
    private static final BigDecimal DEEP_CORRECTION_THRESHOLD = BigDecimal.valueOf(-25);
    private static final int MAX_FLAGGED_HOLDINGS = 5;
    private static final int TOP_HOLDINGS_BASELINE = 3;
    private final PortfolioDerivedMetricsService derivedMetricsService;

    public FlaggedHoldingsBuilder() {
        this(new PortfolioDerivedMetricsService());
    }

    public FlaggedHoldingsBuilder(PortfolioDerivedMetricsService derivedMetricsService) {
        this.derivedMetricsService = derivedMetricsService;
    }

    public List<FlaggedHoldingDto> build(PortfolioReasoningContext context) {
        Set<String> topHoldingSymbols = derivedMetricsService.topHoldings(context, TOP_HOLDINGS_BASELINE).stream()
                .map(EnrichedHoldingData::symbol)
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(symbol -> symbol.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return context.enrichedHoldings().stream()
                .filter(holding -> requiresAttention(holding, topHoldingSymbols))
                .sorted(HoldingClassifier.BY_ALLOCATION_DESC)
                .limit(MAX_FLAGGED_HOLDINGS)
                .map(this::toFlaggedHoldingDto)
                .toList();
    }

    private boolean requiresAttention(EnrichedHoldingData holding, Set<String> topHoldingSymbols) {
        return isTopHolding(holding, topHoldingSymbols) || isSignificantAllocation(holding) || hasRiskFlags(holding);
    }

    private boolean isTopHolding(EnrichedHoldingData holding, Set<String> topHoldingSymbols) {
        return holding.symbol() != null
                && topHoldingSymbols.contains(holding.symbol().trim().toUpperCase(Locale.ROOT));
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
                HoldingClassifier.classifyImportance(holding.allocationPercent()),
                HoldingClassifier.classifyPerformance(holding.profitPercent()),
                holding.valuationFlag(),
                holding.momentumScore(),
                holding.distanceFromHigh(),
                holding.marketCapType(),
                HoldingClassifier.classifyRiskSeverity(riskFlags),
                HoldingClassifier.determinePrimaryRisk(riskFlags),
                riskFlags,
                buildAttentionReasons(holding)
        );
    }

    private List<String> buildAttentionReasons(EnrichedHoldingData holding) {
        List<String> reasons = new ArrayList<>();

        if (holding.allocationPercent() != null
                && holding.allocationPercent().compareTo(HoldingClassifier.CORE_THRESHOLD) > 0) {
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
}
