package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioMetrics;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;

import java.util.List;

public record PortfolioReasoningContext(
        String portfolioId,
        PortfolioSummary portfolioSummary,
        PortfolioMetrics portfolioMetrics,
        List<EnrichedHoldingData> enrichedHoldings
) {

    public PortfolioReasoningContext {
        enrichedHoldings = enrichedHoldings == null ? List.of() : List.copyOf(enrichedHoldings);
    }
}
