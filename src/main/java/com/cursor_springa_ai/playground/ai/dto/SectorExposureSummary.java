package com.cursor_springa_ai.playground.ai.dto;

import java.math.BigDecimal;

public record SectorExposureSummary(
        String sector,
        BigDecimal allocation
) {
}
