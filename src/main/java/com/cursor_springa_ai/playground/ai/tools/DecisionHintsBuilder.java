package com.cursor_springa_ai.playground.ai.tools;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.analytics.model.EnrichedHoldingData;
import com.cursor_springa_ai.playground.ai.dto.PortfolioDecisionHints;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.entity.PortfolioStats;
import com.cursor_springa_ai.playground.model.enums.ConcentrationLevel;
import com.cursor_springa_ai.playground.model.enums.DiversificationLevel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DecisionHintsBuilder {

    private static final BigDecimal SMALL_CAP_RISK_THRESHOLD = BigDecimal.valueOf(25);

    private final PortfolioDerivedMetricsService derivedMetricsService;

    public DecisionHintsBuilder(PortfolioDerivedMetricsService derivedMetricsService) {
        this.derivedMetricsService = derivedMetricsService;
    }

    public PortfolioDecisionHints build(PortfolioReasoningContext context) {
        return build(
                context.portfolioStats(),
                context.classification(),
                context.portfolioRiskFlags(),
                context.enrichedHoldings()
        );
    }

    public PortfolioDecisionHints resolve(PortfolioReasoningContext context) {
        return context.decisionHints() != null ? context.decisionHints() : build(context);
    }

    public PortfolioDecisionHints build(
            PortfolioStats stats,
            PortfolioClassification classification,
            List<String> portfolioRiskFlags,
            List<EnrichedHoldingData> enrichedHoldings
    ) {
        EnrichedHoldingData largestHolding = derivedMetricsService.topHoldings(enrichedHoldings, 1).stream()
                .findFirst()
                .orElse(null);

        BigDecimal largestHoldingPercent = stats == null ? null : stats.getLargestWeight();
        if (largestHoldingPercent == null && largestHolding != null) {
            largestHoldingPercent = largestHolding.allocationPercent();
        }

        BigDecimal smallCapExposure = derivedMetricsService.smallCapExposure(classification, enrichedHoldings);

        return new PortfolioDecisionHints(
                RiskFlagPrioritizer.primaryRisk(portfolioRiskFlags),
                largestHolding == null ? null : largestHolding.symbol(),
                largestHoldingPercent,
                classification != null && DiversificationLevel.POOR.equals(classification.diversificationLevel()),
                classification != null && ConcentrationLevel.CONCENTRATED.equals(classification.concentrationLevel()),
                smallCapExposure != null && smallCapExposure.compareTo(SMALL_CAP_RISK_THRESHOLD) > 0
        );
    }
}
