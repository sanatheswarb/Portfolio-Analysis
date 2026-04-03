package com.cursor_springa_ai.playground.dto.ai;

import java.math.BigDecimal;

public record SectorExposureSummary(
        String sector,
        BigDecimal allocation
) {
}
