package com.cursor_springa_ai.playground.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserHoldingDto(
        Long id,
        String symbol,
        Integer quantity,
        BigDecimal avgPrice,
        BigDecimal closePrice,
        BigDecimal lastPrice,
        BigDecimal investedValue,
        BigDecimal currentValue,
        BigDecimal pnl,
        BigDecimal pnlPercent,
        BigDecimal dayChange,
        BigDecimal dayChangePercent,
        BigDecimal weightPercent,
        LocalDateTime updatedAt
) {
}
