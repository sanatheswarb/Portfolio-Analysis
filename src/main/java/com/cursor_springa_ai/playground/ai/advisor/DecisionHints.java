package com.cursor_springa_ai.playground.ai.advisor;

import java.math.BigDecimal;
import java.util.List;

record DecisionHints(
        String primaryRisk,
        String primaryRiskDriver,
        String largestHoldingSymbol,
        BigDecimal largestHoldingPercent,
        List<String> priorityActions
) {}
