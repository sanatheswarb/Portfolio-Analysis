package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.PortfolioStats;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioReasoningToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void portfolioOverview_returnsDeterministicMetricsAndLargestHoldings() throws Exception {
        PortfolioReasoningTools tools = new PortfolioReasoningTools(sampleContext(), objectMapper);

        String json = tools.portfolioOverview();
        Map<?, ?> payload = objectMapper.readValue(json, Map.class);

        assertEquals("portfolio-1", payload.get("portfolioUserId"));
        assertTrue(json.contains("HIGH_CONCENTRATION"));
        assertTrue(json.contains("INFY"));
    }

    @Test
    void holdingDetails_isCaseInsensitive() {
        PortfolioReasoningTools tools = new PortfolioReasoningTools(sampleContext(), objectMapper);

        String json = tools.holdingDetails("infy");

        assertTrue(json.contains("\"symbol\":\"INFY\""));
        assertTrue(json.contains("HIGH_CONCENTRATION"));
    }

    @Test
    void flaggedHoldings_returnsOnlyRiskFlaggedEntries() {
        PortfolioReasoningTools tools = new PortfolioReasoningTools(sampleContext(), objectMapper);

        String json = tools.flaggedHoldings();

        assertTrue(json.contains("INFY"));
        assertTrue(json.contains("HIGH_CONCENTRATION"));
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
                List.of("HIGH_CONCENTRATION")
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
                List.of("HIGH_VALUATION")
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
                List.of()
        );

        return new PortfolioReasoningContext("portfolio-1", summary, stats, List.of("HIGH_CONCENTRATION"), List.of(infy, tcs, hdfcBank));
    }
}
