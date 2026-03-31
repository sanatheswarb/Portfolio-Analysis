package com.cursor_springa_ai.playground.dto;

import com.cursor_springa_ai.playground.model.RiskFlag;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;


public record EnrichedHoldingData(
        @JsonProperty("symbol")
        String symbol,

        @JsonProperty("asset_type")
        String assetType,

        @JsonProperty("quantity")
        BigDecimal quantity,

        @JsonProperty("average_buy_price")
        BigDecimal averageBuyPrice,

        @JsonProperty("current_price")
        BigDecimal currentPrice,

        @JsonProperty("invested_value")
        BigDecimal investedValue,

        @JsonProperty("current_value")
        BigDecimal currentValue,

        @JsonProperty("profit_loss")
        BigDecimal profitLoss,

        @JsonProperty("sector")
        String sector,

        @JsonProperty("pe")
        BigDecimal pe,

        @JsonProperty("beta")
        BigDecimal beta,

        @JsonProperty("sector_pe")
        BigDecimal sectorPe,

        @JsonProperty("week52_high")
        BigDecimal week52High,

        @JsonProperty("week52_low")
        BigDecimal week52Low,

        @JsonProperty("market_cap_type")
        String marketCapType,

        @JsonProperty("dma200")
        BigDecimal dma200,

        @JsonProperty("allocation_percent")
        BigDecimal allocationPercent,

        @JsonProperty("profit_percent")
        BigDecimal profitPercent,

        @JsonProperty("distance_from_high")
        BigDecimal distanceFromHigh,

        @JsonProperty("risk_flags")
        List<RiskFlag> riskFlags
) {
}
