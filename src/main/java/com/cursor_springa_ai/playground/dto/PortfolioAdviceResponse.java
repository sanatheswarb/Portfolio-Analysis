package com.cursor_springa_ai.playground.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PortfolioAdviceResponse(
        @JsonProperty("risk_overview")
        String riskOverview,

        @JsonProperty("diversification_feedback")
        String diversificationFeedback,

        @JsonProperty("suggestions")
        List<String> suggestions,

        @JsonProperty("cautionary_note")
        String cautionaryNote
) {
}
