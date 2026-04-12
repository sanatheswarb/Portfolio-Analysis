package com.cursor_springa_ai.playground.ai.dto;

import java.math.BigDecimal;

public record PortfolioDecisionHints(
        String primaryRisk,
        String largestHoldingSymbol,
        BigDecimal largestHoldingPercent,
        boolean diversificationNeeded,
        boolean concentrationReductionNeeded,
        boolean smallCapRiskHigh
) {
}
