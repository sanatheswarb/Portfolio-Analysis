package com.cursor_springa_ai.playground.ai.reasoning;

import com.cursor_springa_ai.playground.analytics.model.EnrichedHoldingData;
import com.cursor_springa_ai.playground.analytics.model.PortfolioSummary;
import com.cursor_springa_ai.playground.ai.dto.PortfolioDecisionHints;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.entity.PortfolioStats;

import java.util.List;

public record PortfolioReasoningContext(
        String portfolioUserId,
        PortfolioSummary portfolioSummary,
        PortfolioStats portfolioStats,
        List<String> portfolioRiskFlags,
        List<EnrichedHoldingData> enrichedHoldings,
        PortfolioClassification classification,
        PortfolioDecisionHints decisionHints
) {

    public PortfolioReasoningContext {
        portfolioRiskFlags = portfolioRiskFlags == null ? List.of() : List.copyOf(portfolioRiskFlags);
        enrichedHoldings = enrichedHoldings == null ? List.of() : List.copyOf(enrichedHoldings);
    }
}
