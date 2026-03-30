package com.cursor_springa_ai.playground.dto;

import java.math.BigDecimal;

public record PortfolioAnalysisResponse(
        String portfolioUserId,
        BigDecimal totalInvested,
        BigDecimal totalCurrentValue,
        BigDecimal totalProfitLoss,
        PortfolioAdviceResponse aiInsights
) {
}
