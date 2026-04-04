package com.cursor_springa_ai.playground.ai.tools;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.cursor_springa_ai.playground.ai.tools.HoldingClassificationUtils.classifyImportance;
import static com.cursor_springa_ai.playground.ai.tools.HoldingClassificationUtils.classifyPerformance;

/**
 * Builds the structured, AI-optimal holding details payload used by the holding_details tool.
 * Groups data into reasoning contexts (identity, portfolio, valuation, performance, risk, signals)
 * so the AI can explain why a stock is important or risky without being given a raw data dump.
 */
public class HoldingDetailsBuilder {

    private static final BigDecimal CONCENTRATION_THRESHOLD = BigDecimal.valueOf(20);
    private static final BigDecimal NEAR_HIGH_THRESHOLD = BigDecimal.valueOf(-10);
    private static final BigDecimal PULLBACK_THRESHOLD = BigDecimal.valueOf(-30);
    private static final BigDecimal HIGH_MOMENTUM_THRESHOLD = BigDecimal.valueOf(70);

    private static final List<String> RISK_PRIORITY = List.of(
            "HIGH_CONCENTRATION",
            "DEEP_CORRECTION",
            "HIGH_VALUATION",
            "PROFIT_BOOKING_ZONE",
            "SMALL_CAP_RISK"
    );

    /**
     * Builds a structured details map for the given holding within the full portfolio context.
     *
     * @param holding    the target holding
     * @param allHoldings all portfolio holdings, used to compute portfolio rank
     * @return a map with holding_identity, portfolio_context, valuation_context,
     *         performance_context, risk_context, and signals sections
     */
    public Map<String, Object> build(EnrichedHoldingData holding, List<EnrichedHoldingData> allHoldings) {
        int rank = computePortfolioRank(holding, allHoldings);
        List<String> riskFlags = holding.riskFlags() != null ? holding.riskFlags() : List.of();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("holding_identity", holdingIdentity(holding));
        result.put("portfolio_context", portfolioContext(holding, rank));
        result.put("valuation_context", valuationContext(holding));
        result.put("performance_context", performanceContext(holding));
        result.put("risk_context", riskContext(riskFlags));
        result.put("signals", signals(holding, rank, riskFlags));
        return result;
    }

    private Map<String, Object> holdingIdentity(EnrichedHoldingData holding) {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("symbol", holding.symbol());
        identity.put("sector", holding.sector());
        identity.put("market_cap_type", holding.marketCapType());
        return identity;
    }

    private Map<String, Object> portfolioContext(EnrichedHoldingData holding, int rank) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("allocation_percent", holding.allocationPercent());
        context.put("importance", classifyImportance(holding.allocationPercent()));
        context.put("portfolio_rank", rank);
        context.put("concentration_risk", isConcentrationRisk(holding.allocationPercent()));
        return context;
    }

    private Map<String, Object> valuationContext(EnrichedHoldingData holding) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("valuation_flag", holding.valuationFlag());
        context.put("pe", holding.pe());
        context.put("sector_pe", holding.sectorPe());
        context.put("valuation_gap_percent", computeValuationGap(holding.pe(), holding.sectorPe()));
        return context;
    }

    private Map<String, Object> performanceContext(EnrichedHoldingData holding) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("pnl_percent", holding.profitPercent());
        context.put("performance_status", classifyPerformance(holding.profitPercent()));
        context.put("distance_from_52w_high", holding.distanceFromHigh());
        context.put("momentum_score", holding.momentumScore());
        context.put("trend", classifyTrend(holding.distanceFromHigh()));
        return context;
    }

    private Map<String, Object> riskContext(List<String> riskFlags) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("risk_flags", riskFlags);
        context.put("risk_severity", classifyRiskSeverity(riskFlags));
        context.put("primary_risk", determinePrimaryRisk(riskFlags));
        return context;
    }

    private List<String> signals(EnrichedHoldingData holding, int rank, List<String> riskFlags) {
        List<String> signals = new ArrayList<>();

        if (rank == 1) {
            signals.add("Largest portfolio holding");
        } else if (rank <= 3) {
            signals.add("Top 3 portfolio holding");
        }

        if (holding.distanceFromHigh() != null
                && holding.distanceFromHigh().compareTo(NEAR_HIGH_THRESHOLD) > 0) {
            signals.add("Trading near 52 week high");
        } else if (holding.distanceFromHigh() != null
                && holding.distanceFromHigh().compareTo(PULLBACK_THRESHOLD) < 0) {
            signals.add("Trading well below 52 week high");
        }

        if ("OVERVALUED".equals(holding.valuationFlag())) {
            signals.add("Valuation above sector average");
        } else if ("UNDERVALUED".equals(holding.valuationFlag())) {
            signals.add("Valuation below sector average");
        }

        if (holding.momentumScore() != null
                && holding.momentumScore().compareTo(HIGH_MOMENTUM_THRESHOLD) >= 0) {
            signals.add("Momentum remains strong");
        }

        if (holding.profitPercent() != null
                && holding.profitPercent().compareTo(BigDecimal.ZERO) > 0) {
            signals.add("Strong portfolio contributor");
        } else if (holding.profitPercent() != null
                && holding.profitPercent().compareTo(BigDecimal.ZERO) < 0) {
            signals.add("Position currently at a loss");
        }

        if (riskFlags.contains("HIGH_CONCENTRATION")) {
            signals.add("High concentration warrants monitoring");
        }

        return List.copyOf(signals);
    }

    private int computePortfolioRank(EnrichedHoldingData target, List<EnrichedHoldingData> allHoldings) {
        List<EnrichedHoldingData> sorted = allHoldings.stream()
                .filter(h -> h.allocationPercent() != null)
                .sorted(Comparator.comparing(EnrichedHoldingData::allocationPercent).reversed())
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (target.symbol() != null && target.symbol().equalsIgnoreCase(sorted.get(i).symbol())) {
                return i + 1;
            }
        }
        return sorted.size() + 1;
    }

    private boolean isConcentrationRisk(BigDecimal allocationPercent) {
        return allocationPercent != null && allocationPercent.compareTo(CONCENTRATION_THRESHOLD) > 0;
    }

    private BigDecimal computeValuationGap(BigDecimal pe, BigDecimal sectorPe) {
        if (pe == null || sectorPe == null || sectorPe.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return pe.subtract(sectorPe)
                .divide(sectorPe, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String classifyTrend(BigDecimal distanceFromHigh) {
        if (distanceFromHigh == null) {
            return null;
        }
        if (distanceFromHigh.compareTo(NEAR_HIGH_THRESHOLD) > 0) {
            return "NEAR_HIGH";
        }
        if (distanceFromHigh.compareTo(PULLBACK_THRESHOLD) > 0) {
            return "PULLBACK";
        }
        return "DEEP_CORRECTION";
    }

    private String classifyRiskSeverity(List<String> riskFlags) {
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

    private String determinePrimaryRisk(List<String> riskFlags) {
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
