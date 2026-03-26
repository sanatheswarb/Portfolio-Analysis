package com.cursor_springa_ai.playground.dto;

import com.cursor_springa_ai.playground.model.AssetType;

import java.math.BigDecimal;

public record HoldingPerformance(
        String symbol,
        AssetType assetType,
        BigDecimal quantity,
        BigDecimal averageBuyPrice,
        BigDecimal currentPrice,
        BigDecimal investedValue,
        BigDecimal currentValue,
        BigDecimal profitLoss
) {
}
