package com.cursor_springa_ai.playground.dto.ai;

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
