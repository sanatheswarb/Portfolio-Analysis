package com.cursor_springa_ai.playground.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record PortfolioMetrics(
        @JsonProperty("total_invested")
        BigDecimal totalInvested,

        @JsonProperty("total_current_value")
        BigDecimal totalCurrentValue,

        @JsonProperty("total_pnl")
        BigDecimal totalPnL,

        @JsonProperty("total_pnl_percent")
        BigDecimal totalPnLPercent,

        @JsonProperty("total_holdings")
        int totalHoldings,

        @JsonProperty("top_holding_percent")
        BigDecimal topHoldingPercent,

        @JsonProperty("top_3_holding_percent")
        BigDecimal top3HoldingPercent,

        @JsonProperty("sector_exposure")
        Map<String, BigDecimal> sectorExposure,

        @JsonProperty("sector_count")
        Map<String, Integer> sectorCount,

        @JsonProperty("portfolio_risk_flags")
        List<String> portfolioRiskFlags,

        @JsonProperty("diversification_score")
        BigDecimal diversificationScore
) {
}
