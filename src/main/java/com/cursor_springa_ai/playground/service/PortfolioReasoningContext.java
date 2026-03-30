package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.PortfolioStats;

import java.util.List;

public record PortfolioReasoningContext(
        String portfolioUserId,
        PortfolioSummary portfolioSummary,
        PortfolioStats portfolioStats,
        List<String> portfolioRiskFlags,
        List<EnrichedHoldingData> enrichedHoldings
) {

    public PortfolioReasoningContext {
        portfolioRiskFlags = portfolioRiskFlags == null ? List.of() : List.copyOf(portfolioRiskFlags);
        enrichedHoldings = enrichedHoldings == null ? List.of() : List.copyOf(enrichedHoldings);
    }
}
