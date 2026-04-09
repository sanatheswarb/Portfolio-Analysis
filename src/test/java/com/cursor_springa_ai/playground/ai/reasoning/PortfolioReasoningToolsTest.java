package com.cursor_springa_ai.playground.ai.reasoning;

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
        assertEquals(RiskFlag.HIGH_CONCENTRATION.name(), payload.path("decision_hints").path("primary_risk").asText());
        assertEquals("INFY", payload.path("decision_hints").path("largest_holding_symbol").asText());
        assertTrue(payload.path("decision_hints").path("concentration_reduction_needed").asBoolean());
        assertEquals("INFY", payload.path("largest_holdings").get(0).path("symbol").asText());
        assertTrue(json.contains(RiskFlag.HIGH_CONCENTRATION.name()));
        assertTrue(payload.path("portfolio_strengths").toString().contains("Overall performance is healthy."));
        assertEquals("Largest holding concentration is high.", payload.path("portfolio_concerns").get(0).asText());
        assertEquals("technology sector exposure is high at 65%.", payload.path("portfolio_concerns").get(1).asText());
    }

    @Test
    void holdingsList_returnsSummaryForAllHoldingsSortedByAllocation() throws Exception {
        PortfolioReasoningTools tools = new PortfolioReasoningTools(sampleContext(), objectMapper);

        String json = tools.holdingsList();
        JsonNode payload = objectMapper.readTree(json);

        assertEquals(3, payload.size());
        // sorted by allocation descending
        assertEquals("INFY", payload.get(0).path("symbol").asText());
        assertEquals(35, payload.get(0).path("allocation_percent").asInt());
        assertEquals("TCS", payload.get(1).path("symbol").asText());
        assertEquals("HDFCBANK", payload.get(2).path("symbol").asText());
        // minimal fields only — no sector, no pe, etc.
        assertFalse(payload.get(0).has("sector"));
        assertFalse(payload.get(0).has("pe"));
    }

    @Test
    void holdingsList_includesValuationAndRiskFlags() throws Exception {
        PortfolioReasoningTools tools = new PortfolioReasoningTools(sampleContext(), objectMapper);

        String json = tools.holdingsList();
        JsonNode infy = objectMapper.readTree(json).get(0);

        assertEquals("OVERVALUED", infy.path("valuation_flag").asText());
        assertTrue(infy.path("risk_flags").toString().contains(RiskFlag.HIGH_CONCENTRATION.name()));
        // pnl_percent field is present
        assertTrue(infy.has("pnl_percent"));
    }

    @Test
    void holdingDetails_isCaseInsensitiveAndReturnsStructuredSections() throws Exception {
        PortfolioReasoningTools tools = new PortfolioReasoningTools(sampleContext(), objectMapper);

        String json = tools.holdingDetails(List.of("infy"));
        JsonNode payload = objectMapper.readTree(json);

        JsonNode infy = payload.get(0);
        assertEquals("INFY", infy.path("holding_identity").path("symbol").asText());
        assertEquals("technology", infy.path("holding_identity").path("sector").asText());
        assertEquals("largecap", infy.path("holding_identity").path("market_cap_type").asText());
        assertTrue(infy.has("portfolio_context"));
        assertTrue(infy.has("valuation_context"));
        assertTrue(infy.has("performance_context"));
        assertTrue(infy.has("risk_context"));
        assertTrue(infy.has("signals"));
    }

    @Test
    void holdingDetails_portfolioContextHasCorrectFields() throws Exception {
        PortfolioReasoningTools tools = new PortfolioReasoningTools(sampleContext(), objectMapper);

        String json = tools.holdingDetails(List.of("INFY"));
        JsonNode portfolioCtx = objectMapper.readTree(json).get(0).path("portfolio_context");

        assertEquals(35, portfolioCtx.path("allocation_percent").asInt());
        assertEquals("CORE", portfolioCtx.path("importance").asText());
        assertEquals(1, portfolioCtx.path("portfolio_rank").asInt());
        assertTrue(portfolioCtx.path("concentration_risk").asBoolean());
    }

    @Test
    void holdingDetails_valuationContextHasGapPercent() throws Exception {
        PortfolioReasoningTools tools = new PortfolioReasoningTools(sampleContext(), objectMapper);

        // INFY: pe=28, sectorPe=22 → gap = (28-22)/22 * 100 ≈ 27.27
        String json = tools.holdingDetails(List.of("INFY"));
        JsonNode valuationCtx = objectMapper.readTree(json).get(0).path("valuation_context");

        assertEquals("OVERVALUED", valuationCtx.path("valuation_flag").asText());
        assertEquals(28, valuationCtx.path("pe").asInt());
        assertEquals(22, valuationCtx.path("sector_pe").asInt());
        assertTrue(valuationCtx.path("valuation_gap_percent").asDouble() > 0);
    }

    @Test
    void holdingDetails_performanceContextHasTrendAndStatus() throws Exception {
        PortfolioReasoningTools tools = new PortfolioReasoningTools(sampleContext(), objectMapper);

        // INFY: distanceFromHigh=-7.89 → NEAR_HIGH, profitPercent=16.67 → PROFIT
        String json = tools.holdingDetails(List.of("INFY"));
        JsonNode perfCtx = objectMapper.readTree(json).get(0).path("performance_context");

        assertEquals("PROFIT", perfCtx.path("performance_status").asText());
        assertEquals("NEAR_HIGH", perfCtx.path("trend").asText());
        assertTrue(perfCtx.has("momentum_score"));
    }

    @Test
    void holdingDetails_riskContextAndSignals() throws Exception {
        PortfolioReasoningTools tools = new PortfolioReasoningTools(sampleContext(), objectMapper);

        String json = tools.holdingDetails(List.of("INFY"));
        JsonNode infy = objectMapper.readTree(json).get(0);
        JsonNode riskCtx = infy.path("risk_context");
        JsonNode signals = infy.path("signals");

        assertTrue(riskCtx.path("risk_flags").toString().contains(RiskFlag.HIGH_CONCENTRATION.name()));
        assertEquals(RiskFlag.HIGH_CONCENTRATION.name(), riskCtx.path("primary_risk").asText());
        assertTrue(signals.toString().contains("Largest portfolio holding"));
        assertTrue(signals.toString().contains("Trading near 52 week high"));
    }

    @Test
    void holdingDetails_supportsMultipleSymbols() throws Exception {
        PortfolioReasoningTools tools = new PortfolioReasoningTools(sampleContext(), objectMapper);

        String json = tools.holdingDetails(List.of("INFY", "TCS"));
        JsonNode payload = objectMapper.readTree(json);

        assertEquals(2, payload.size());
        assertEquals("INFY", payload.get(0).path("holding_identity").path("symbol").asText());
        assertEquals("TCS", payload.get(1).path("holding_identity").path("symbol").asText());
    }

    @Test
    void holdingDetails_returnsErrorForUnknownSymbol() throws Exception {
        PortfolioReasoningTools tools = new PortfolioReasoningTools(sampleContext(), objectMapper);

        String json = tools.holdingDetails(List.of("UNKNOWN"));
        JsonNode payload = objectMapper.readTree(json);

        assertTrue(payload.get(0).has("error"));
    }

    @Test
    void flaggedHoldings_returnsAttentionHoldingsSortedByAllocation() throws Exception {
        PortfolioReasoningTools tools = new PortfolioReasoningTools(sampleContext(), objectMapper);

        String json = tools.flaggedHoldings();
        JsonNode payload = objectMapper.readTree(json);

        // All three holdings qualify: INFY (35%), TCS (30%), HDFCBANK (20%) all have allocation > 10%
        assertEquals(3, payload.size());
        // sorted by allocation descending
        assertEquals("INFY", payload.get(0).path("symbol").asText());
        assertEquals("TCS", payload.get(1).path("symbol").asText());
        assertEquals("HDFCBANK", payload.get(2).path("symbol").asText());
    }

    @Test
    void flaggedHoldings_enrichedFieldsArePopulated() throws Exception {
        PortfolioReasoningTools tools = new PortfolioReasoningTools(sampleContext(), objectMapper);

        String json = tools.flaggedHoldings();
        JsonNode infy = objectMapper.readTree(json).get(0);

        assertEquals("CORE", infy.path("importance").asText());
        assertEquals("PROFIT", infy.path("performance_status").asText());
        assertEquals("OVERVALUED", infy.path("valuation").asText());
        assertEquals("LOW", infy.path("risk_severity").asText());
        assertEquals(RiskFlag.HIGH_CONCENTRATION.name(), infy.path("primary_concern").asText());
        assertTrue(infy.path("risk_flags").toString().contains(RiskFlag.HIGH_CONCENTRATION.name()));
        assertTrue(infy.path("attention_reasons").toString().contains("Largest portfolio allocation"));
        assertTrue(infy.path("attention_reasons").toString().contains("Valuation above sector average"));
        assertTrue(infy.path("attention_reasons").toString().contains("Trading near 52 week high"));
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
            ),
            null
        );
    }
}
