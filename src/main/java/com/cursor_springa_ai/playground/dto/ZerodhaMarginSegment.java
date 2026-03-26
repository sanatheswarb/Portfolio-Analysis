package com.cursor_springa_ai.playground.dto;

/**
 * Represents margin data for a single segment (equity or commodity).
 * Maps fields from {@link com.zerodhatech.models.Margin}.
 * FREE API – accessible with developer-console registration and OAuth session only.
 */
public record ZerodhaMarginSegment(
        String net,
        AvailableMargin available,
        UtilisedMargin utilised
) {

    public record AvailableMargin(
            String adhocMargin,
            String cash,
            String liveBalance,
            String collateral,
            String intradayPayin
    ) {}

    public record UtilisedMargin(
            String debits,
            String exposure,
            String m2mRealised,
            String m2mUnrealised,
            String optionPremium,
            String span,
            String holdingSales,
            String turnover
    ) {}
}
