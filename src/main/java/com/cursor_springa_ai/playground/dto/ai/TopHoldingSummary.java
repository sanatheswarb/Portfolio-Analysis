package com.cursor_springa_ai.playground.dto.ai;

import java.math.BigDecimal;
import java.util.List;

public record TopHoldingSummary(
        String symbol,
        BigDecimal allocation,
        List<String> riskFlags
) {
}
