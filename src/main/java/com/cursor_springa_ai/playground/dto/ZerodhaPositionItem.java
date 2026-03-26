package com.cursor_springa_ai.playground.dto;

/**
 * Single position entry returned by the Zerodha positions API.
 * Maps fields from {@link com.zerodhatech.models.Position}.
 * FREE API – accessible with developer-console registration and OAuth session only.
 */
public record ZerodhaPositionItem(
        String tradingSymbol,
        String exchange,
        String product,
        String instrumentToken,
        int netQuantity,
        int buyQuantity,
        int sellQuantity,
        int overnightQuantity,
        double buyPrice,
        double sellPrice,
        double averagePrice,
        double lastPrice,
        double closePrice,
        double pnl,
        double realised,
        double unrealised,
        double m2m,
        double buyValue,
        double sellValue,
        double netValue
) {}
