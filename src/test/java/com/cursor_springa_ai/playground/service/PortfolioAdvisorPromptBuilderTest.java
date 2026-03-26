package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioAdvisorPromptBuilderTest {

    private final PortfolioAdvisorPromptBuilder builder = new PortfolioAdvisorPromptBuilder(new ObjectMapper());

    @Test
    void buildEnrichedHoldingsJson_excludesPeFromOutput() {
        EnrichedHoldingData holding = new EnrichedHoldingData(
                "ABC",
                "equity",
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(55),
                BigDecimal.valueOf(5000),
                BigDecimal.valueOf(5500),
                BigDecimal.valueOf(500),
                "financial",
                BigDecimal.valueOf(15),
                BigDecimal.valueOf(1.2),
                BigDecimal.valueOf(14),
                BigDecimal.valueOf(120),
                BigDecimal.valueOf(80),
                "largecap",
                BigDecimal.valueOf(40),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(15),
                BigDecimal.valueOf(10),
                List.of("high_volatility")
        );

        String json = builder.buildEnrichedHoldingsJson(List.of(holding));

        assertNotNull(json);
        assertTrue(json.contains("ABC"));
        assertFalse(json.contains("\"pe\""));
    }

    @Test
    void buildSystemPrompt_containsBaseRules() {
        String prompt = builder.buildSystemPrompt();

        assertNotNull(prompt);
        assertTrue(prompt.contains("PRIMARY OBJECTIVE"));
        assertTrue(prompt.contains("OUTPUT REQUIREMENTS"));
        assertTrue(prompt.contains("getPortfolioMetrics tool"));
    }

    @Test
    void buildPortfolioDataWithMetrics_usesToolCallingInstructions() {
        PortfolioSummary summary = new PortfolioSummary(BigDecimal.valueOf(10000), BigDecimal.valueOf(11000), BigDecimal.valueOf(1000), BigDecimal.valueOf(10), 2);

        String data = builder.buildPortfolioDataWithMetrics(null, List.of(), summary);

        assertNotNull(data);
        assertTrue(data.contains("Portfolio Summary"));
        assertTrue(data.contains("getPortfolioMetrics tool"));
        assertTrue(data.contains("Holdings (enriched with market metrics and risk flags)"));
        assertFalse(data.contains("Portfolio Metrics:"));
    }
}
