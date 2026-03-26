package com.cursor_springa_ai.playground.dto;

/**
 * Single mutual-fund holding entry returned by the Zerodha MF holdings API.
 * Maps fields from {@link com.zerodhatech.models.MFHolding}.
 * FREE API – accessible with developer-console registration and OAuth session only.
 */
public record ZerodhaMFHoldingItem(
        String tradingSymbol,
        String fund,
        String folio,
        double quantity,
        double averagePrice,
        double lastPrice,
        double pnl
) {}
