package com.cursor_springa_ai.playground.importer;

import java.math.BigDecimal;

/**
 * Immutable transfer object carrying all financial values computed for a single holding
 * during a Zerodha import. Produced by {@link HoldingValueCalculator} and consumed by
 * {@link ZerodhaImportService} to build the {@code UserHolding} entity.
 */
public record HoldingComputedValues(
        String symbol,
        int quantity,
        BigDecimal avgPrice,
        BigDecimal lastPrice,
        BigDecimal closePrice,
        BigDecimal investedValue,
        BigDecimal currentValue,
        BigDecimal pnl,
        BigDecimal pnlPercent,
        BigDecimal dayChange,
        BigDecimal dayChangePct,
        BigDecimal weightPercent
) {}
