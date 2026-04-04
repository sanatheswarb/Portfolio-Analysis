package com.cursor_springa_ai.playground.dto.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Minimal AI-facing DTO for the holdings_list tool.
 * Contains only summary fields so the AI can quickly scan all holdings
 * before deciding which symbols to inspect in detail.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record HoldingListItemDto(

        @JsonProperty("symbol")
        String symbol,

        @JsonProperty("allocation_percent")
        BigDecimal allocationPercent,

        @JsonProperty("pnl_percent")
        BigDecimal pnlPercent,

        @JsonProperty("valuation_flag")
        String valuationFlag,

        @JsonProperty("risk_flags")
        List<String> riskFlags
) {
}
