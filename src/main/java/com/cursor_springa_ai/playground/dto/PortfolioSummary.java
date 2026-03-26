package com.cursor_springa_ai.playground.dto;

import java.math.BigDecimal;

public record PortfolioSummary(
    BigDecimal totalInvested,
    BigDecimal totalCurrentValue,
    BigDecimal totalPnL,
    BigDecimal totalPnLPercent,
    int totalHoldings
) {}