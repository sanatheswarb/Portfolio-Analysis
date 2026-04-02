package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.PortfolioStats;
import com.cursor_springa_ai.playground.model.RiskFlag;
import com.cursor_springa_ai.playground.model.enums.ConcentrationLevel;
import com.cursor_springa_ai.playground.model.enums.DiversificationLevel;
import com.cursor_springa_ai.playground.model.enums.PerformanceLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioRiskLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioStyle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioReasoningToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void portfolioOverview_returnsDeterministicMetricsAndLargestHoldings() throws Exception {
        PortfolioReasoningTools tools = new PortfolioReasoningTools(sampleContext(), objectMapper);

        String json = tools.portfolioOverview();
        JsonNode payload = objectMapper.readTree(json);

        assertEquals("GROWTH_HEAVY", payload.path("portfolio_identity").path("portfolio_style").asText());
        assertEquals("HIGH", payload.path("portfolio_identity").path("risk_level").asText());
        assertEquals("HIGH_RISK_GROWTH_HEAVY", payload.path("portfolio_identity").path("portfolio_health_summary").asText());
        assertEquals(3, payload.path("portfolio_structure").path("stock_count").asInt());
        assertEquals(35, payload.path("portfolio_structure").path("largest_holding_percent").asInt());
        assertEquals("HIGH_CONCENTRATION", payload.path("portfolio_structure").path("largest_holding_assessment").asText());
        assertEquals(65, payload.path("portfolio_structure").path("top_sector_percent").asInt());
        assertEquals("SECTOR_CONCENTRATION", payload.path("portfolio_structure").path("sector_concentration_assessment").asText());
        assertEquals(85, payload.path("portfolio_structure").path("large_cap_exposure").asInt());
        assertEquals("GOOD", payload.path("portfolio_performance").path("performance_level").asText());
        assertEquals("HIGH", payload.path("portfolio_risk_profile").path("risk_level").asText());
        assertEquals("INFY", payload.path("largest_holdings").get(0).path("symbol").asText());
        assertTrue(json.contains(RiskFlag.HIGH_CONCENTRATION.name()));
        assertTrue(payload.path("portfolio_strengths").toString().contains("Overall performance is healthy."));
        assertTrue(payload.path("portfolio_concerns").toString().contains("technology sector exposure is high at 65%."));
    }

    @Test
    void holdingDetails_isCaseInsensitive() {
        PortfolioReasoningTools tools = new PortfolioReasoningTools(sampleContext(), objectMapper);

        String json = tools.holdingDetails("infy");

        assertTrue(json.contains("\"symbol\":\"INFY\""));
        assertTrue(json.contains(RiskFlag.HIGH_CONCENTRATION.name()));
    }

    @Test
    void flaggedHoldings_returnsOnlyRiskFlaggedEntries() {
        PortfolioReasoningTools tools = new PortfolioReasoningTools(sampleContext(), objectMapper);

        String json = tools.flaggedHoldings();

        assertTrue(json.contains("INFY"));
        assertTrue(json.contains(RiskFlag.HIGH_CONCENTRATION.name()));
        assertTrue(json.contains("TCS"));
        assertFalse(json.contains("HDFCBANK"));
    }

    private PortfolioReasoningContext sampleContext() {
        PortfolioSummary summary = new PortfolioSummary(
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(112500),
                BigDecimal.valueOf(12500),
                BigDecimal.valueOf(12.5),
                3
        );
        PortfolioStats stats = new PortfolioStats(1L,
                BigDecimal.valueOf(100000), BigDecimal.valueOf(112500),
                BigDecimal.valueOf(12500), BigDecimal.valueOf(12.5),
                BigDecimal.valueOf(35), 3,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(65), BigDecimal.valueOf(48),
                null);

        EnrichedHoldingData infy = new EnrichedHoldingData(
                "INFY",
                "equity",
                BigDecimal.TEN,
                BigDecimal.valueOf(1500),
                BigDecimal.valueOf(1750),
                BigDecimal.valueOf(15000),
                BigDecimal.valueOf(17500),
                BigDecimal.valueOf(2500),
                "technology",
                BigDecimal.valueOf(28),
                BigDecimal.valueOf(1.1),
                BigDecimal.valueOf(22),
                BigDecimal.valueOf(1900),
                BigDecimal.valueOf(1100),
                "largecap",
                BigDecimal.valueOf(1400),
                BigDecimal.valueOf(35),
                BigDecimal.valueOf(16.67),
                BigDecimal.valueOf(-7.89),
                "OVERVALUED",
                BigDecimal.valueOf(92.11),
                BigDecimal.valueOf(4),
                List.of(RiskFlag.HIGH_CONCENTRATION.name())
        );

        EnrichedHoldingData tcs = new EnrichedHoldingData(
                "TCS",
                "equity",
                BigDecimal.valueOf(12),
                BigDecimal.valueOf(3200),
                BigDecimal.valueOf(3333),
                BigDecimal.valueOf(38400),
                BigDecimal.valueOf(39996),
                BigDecimal.valueOf(1596),
                "technology",
                BigDecimal.valueOf(26),
                BigDecimal.valueOf(0.9),
                BigDecimal.valueOf(22),
                BigDecimal.valueOf(3600),
                BigDecimal.valueOf(2900),
                "largecap",
                BigDecimal.valueOf(3100),
                BigDecimal.valueOf(30),
                BigDecimal.valueOf(4.16),
                BigDecimal.valueOf(-7.42),
                "FAIRLY_VALUED",
                BigDecimal.valueOf(92.58),
                BigDecimal.valueOf(3),
                List.of(RiskFlag.HIGH_VALUATION.name())
        );

        EnrichedHoldingData hdfcBank = new EnrichedHoldingData(
                "HDFCBANK",
                "equity",
                BigDecimal.valueOf(8),
                BigDecimal.valueOf(1450),
                BigDecimal.valueOf(1500),
                BigDecimal.valueOf(11600),
                BigDecimal.valueOf(12000),
                BigDecimal.valueOf(400),
                "financials",
                BigDecimal.valueOf(19),
                BigDecimal.valueOf(0.8),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(1800),
                BigDecimal.valueOf(1200),
                "largecap",
                BigDecimal.valueOf(1420),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(3.45),
                BigDecimal.valueOf(-16.67),
                "FAIRLY_VALUED",
                BigDecimal.valueOf(83.33),
                BigDecimal.valueOf(2),
                List.of()
        );

        return new PortfolioReasoningContext(
                "portfolio-1",
                summary,
                stats,
                List.of(RiskFlag.HIGH_CONCENTRATION.name()),
                List.of(infy, tcs, hdfcBank),
                new PortfolioClassification(
                        PortfolioRiskLevel.HIGH,
                        DiversificationLevel.AVERAGE,
                        ConcentrationLevel.CONCENTRATED,
                        PerformanceLevel.GOOD,
                        PortfolioStyle.GROWTH_HEAVY,
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(65)
                )
        );
    }
}
