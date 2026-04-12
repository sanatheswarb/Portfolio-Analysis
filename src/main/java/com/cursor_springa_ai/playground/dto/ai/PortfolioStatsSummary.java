package com.cursor_springa_ai.playground.dto.ai;

import java.math.BigDecimal;

public record PortfolioStatsSummary(
        int stockCount,
        BigDecimal largestHoldingPercent,
        BigDecimal top3Percent,
        BigDecimal pnlPercent,
        BigDecimal smallCapExposure
) {
}
