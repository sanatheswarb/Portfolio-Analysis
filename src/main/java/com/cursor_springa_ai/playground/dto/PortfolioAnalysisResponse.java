package com.cursor_springa_ai.playground.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioAnalysisResponse(
        String portfolioId,
        String ownerName,
        BigDecimal totalInvested,
        BigDecimal totalCurrentValue,
        BigDecimal totalProfitLoss,
        List<HoldingPerformance> holdings,
        PortfolioAdviceResponse aiInsights
) {
}
