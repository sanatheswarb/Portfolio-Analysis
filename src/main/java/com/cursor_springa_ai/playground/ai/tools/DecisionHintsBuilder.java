package com.cursor_springa_ai.playground.ai.tools;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.ai.PortfolioDecisionHints;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.PortfolioStats;
import com.cursor_springa_ai.playground.model.enums.ConcentrationLevel;
import com.cursor_springa_ai.playground.model.enums.DiversificationLevel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DecisionHintsBuilder {

    private static final Map<String, Integer> RISK_PRIORITY = riskPriority();
    private static final BigDecimal SMALL_CAP_RISK_THRESHOLD = BigDecimal.valueOf(25);

    private final PortfolioDerivedMetricsService derivedMetricsService;

    public DecisionHintsBuilder(PortfolioDerivedMetricsService derivedMetricsService) {
        this.derivedMetricsService = derivedMetricsService;
    }

    public PortfolioDecisionHints build(PortfolioReasoningContext context) {
        PortfolioStats stats = context.portfolioStats();
        PortfolioClassification classification = context.classification();

        EnrichedHoldingData largestHolding = derivedMetricsService.topHoldings(context, 1).stream()
                .findFirst()
                .orElse(null);

        BigDecimal largestHoldingPercent = stats == null ? null : stats.getLargestWeight();
        if (largestHoldingPercent == null && largestHolding != null) {
            largestHoldingPercent = largestHolding.allocationPercent();
        }

        BigDecimal smallCapExposure = derivedMetricsService.smallCapExposure(context);

        return new PortfolioDecisionHints(
                primaryRisk(context.portfolioRiskFlags()),
                largestHolding == null ? null : largestHolding.symbol(),
                largestHoldingPercent,
                classification != null && DiversificationLevel.POOR.equals(classification.diversificationLevel()),
                classification != null && ConcentrationLevel.CONCENTRATED.equals(classification.concentrationLevel()),
                smallCapExposure != null && smallCapExposure.compareTo(SMALL_CAP_RISK_THRESHOLD) > 0
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
