package com.cursor_springa_ai.playground.ai.tools;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.ai.AnalysisDecisionTrace;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.PortfolioStats;
import com.cursor_springa_ai.playground.model.enums.ConcentrationLevel;
import com.cursor_springa_ai.playground.model.enums.DiversificationLevel;
import com.cursor_springa_ai.playground.model.enums.PerformanceLevel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds an {@link AnalysisDecisionTrace} from a {@link PortfolioReasoningContext}.
 *
 * <p>The trace captures <em>decision inputs</em> — facts that caused the AI advice —
 * not the AI output itself.  Storing these facts makes follow-up chat deterministic.
 */
@Component
public class DecisionTraceBuilder {

    private static final Map<String, Integer> RISK_PRIORITY = riskPriority();
    private static final int TOP_RISK_DRIVERS_LIMIT = 5;
    private static final BigDecimal TOP3_CONCENTRATION_THRESHOLD_PERCENT = BigDecimal.valueOf(60);

    private final PortfolioDerivedMetricsService derivedMetricsService;

    public DecisionTraceBuilder(PortfolioDerivedMetricsService derivedMetricsService) {
        this.derivedMetricsService = derivedMetricsService;
    }

    public AnalysisDecisionTrace build(PortfolioReasoningContext context) {
        EnrichedHoldingData largestHolding = derivedMetricsService.topHoldings(context, 1)
                .stream().findFirst().orElse(null);

        return new AnalysisDecisionTrace(
                primaryRisk(context.portfolioRiskFlags()),
                largestHolding != null ? largestHolding.symbol() : null,
                topRiskDrivers(context),
                mainDiversificationIssue(context),
                mainStrength(context)
        );
    }

    private String primaryRisk(List<String> riskFlags) {
        return riskFlags.stream()
                .min((left, right) -> Integer.compare(priority(left), priority(right)))
                .orElse(null);
    }

    private int priority(String riskFlag) {
        return RISK_PRIORITY.getOrDefault(riskFlag, Integer.MAX_VALUE);
    }

    private List<String> topRiskDrivers(PortfolioReasoningContext context) {
        List<String> drivers = new ArrayList<>();

        // Add top flagged holding symbols first
        derivedMetricsService.topHoldings(context, TOP_RISK_DRIVERS_LIMIT).stream()
                .filter(h -> h.riskFlags() != null && !h.riskFlags().isEmpty())
                .map(EnrichedHoldingData::symbol)
                .forEach(drivers::add);

        // Add small-cap exposure as a driver if significant
        BigDecimal smallCapExposure = derivedMetricsService.smallCapExposure(context);
        if (smallCapExposure != null && smallCapExposure.compareTo(BigDecimal.valueOf(20)) > 0
                && drivers.size() < TOP_RISK_DRIVERS_LIMIT) {
            drivers.add("Small cap exposure");
        }

        return drivers.size() > TOP_RISK_DRIVERS_LIMIT
                ? drivers.subList(0, TOP_RISK_DRIVERS_LIMIT)
                : drivers;
    }

    private String mainDiversificationIssue(PortfolioReasoningContext context) {
        PortfolioClassification classification = context.classification();
        PortfolioStats stats = context.portfolioStats();

        if (classification != null && ConcentrationLevel.CONCENTRATED.equals(classification.concentrationLevel())) {
            BigDecimal top3 = stats != null ? stats.getTop3HoldingPercent() : null;
            if (top3 != null && top3.compareTo(TOP3_CONCENTRATION_THRESHOLD_PERCENT) > 0) {
                return "Top 3 holdings exceed " + top3.stripTrailingZeros().toPlainString() + "%";
            }
            return "Portfolio is highly concentrated";
        }

        if (classification != null && DiversificationLevel.POOR.equals(classification.diversificationLevel())) {
            return "Poor sector diversification";
        }

        return null;
    }

    private String mainStrength(PortfolioReasoningContext context) {
        PortfolioStats stats = context.portfolioStats();
        PortfolioClassification classification = context.classification();

        if (stats != null && stats.getTotalPnl() != null
                && stats.getTotalPnl().compareTo(BigDecimal.ZERO) > 0) {
            return "Portfolio profitable";
        }

        if (classification != null
                && (PerformanceLevel.GOOD.equals(classification.performanceLevel())
                    || PerformanceLevel.STRONG.equals(classification.performanceLevel()))) {
            return "Good portfolio performance";
        }

        return null;
    }

    private static Map<String, Integer> riskPriority() {
        Map<String, Integer> priority = new LinkedHashMap<>();
        priority.put("HIGH_CONCENTRATION", 1);
        priority.put("UNDER_DIVERSIFIED", 2);
        priority.put("TOP_HEAVY_PORTFOLIO", 3);
        priority.put("SMALL_CAP_RISK", 4);
        priority.put("HIGH_VALUATION", 5);
        priority.put("DEEP_CORRECTION", 6);
        priority.put("PROFIT_BOOKING_ZONE", 7);
        return Map.copyOf(priority);
    }
}
