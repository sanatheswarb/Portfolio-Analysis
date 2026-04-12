package com.cursor_springa_ai.playground.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI-facing DTO representing a holding that requires attention.
 * Provides structured context so the AI can give actionable insights
 * rather than generic advice.
 */
public record FlaggedHoldingDto(

        @JsonProperty("symbol")
        String symbol,

        @JsonProperty("allocation_percent")
        BigDecimal allocationPercent,

        @JsonProperty("importance")
        String importance,

        @JsonProperty("performance_status")
        String performanceStatus,

        @JsonProperty("valuation")
        String valuation,

        @JsonProperty("momentum_score")
        BigDecimal momentumScore,

        @JsonProperty("distance_from_52w_high")
        BigDecimal distanceFrom52wHigh,

        @JsonProperty("market_cap_type")
        String marketCapType,

        @JsonProperty("risk_severity")
        String riskSeverity,

        @JsonProperty("primary_concern")
        String primaryConcern,

        @JsonProperty("risk_flags")
        List<String> riskFlags,

        @JsonProperty("attention_reasons")
        List<String> attentionReasons
) {
}
