package com.cursor_springa_ai.playground.ai.advisor;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.RiskFlag;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                "FAIRLY_VALUED",
                BigDecimal.valueOf(45.83),
                BigDecimal.valueOf(3),
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
        assertTrue(prompt.contains("MUST call portfolio_overview"));
        assertTrue(prompt.contains("RESPONSE RULES"));
        assertTrue(prompt.contains("TOOL RULES"));
        assertTrue(prompt.contains("PORTFOLIO CLASSIFICATION RULES"));
        assertTrue(prompt.contains("SUGGESTION ALIGNMENT RULES"));
        assertTrue(prompt.contains("EXPLANATION RULES"));
        assertTrue(prompt.contains("DO NOT"));
        assertFalse(prompt.contains("RESPONSE FORMAT"));
    }

    @Test
    void buildReasoningRequest_containsCompactToolCallingInstructions() {
        PortfolioSummary summary = new PortfolioSummary(BigDecimal.valueOf(10000), BigDecimal.valueOf(11000), BigDecimal.valueOf(1000), BigDecimal.valueOf(10), 2);
        PortfolioReasoningContext reasoningContext = new PortfolioReasoningContext(
                "portfolio-1",
                summary,
                null,
                List.of(RiskFlag.HIGH_CONCENTRATION.name()),
                List.of(),
                null
        );

        String data = builder.buildReasoningRequest(reasoningContext);

        assertNotNull(data);
        assertTrue(data.contains("portfolio_userId: portfolio-1"));
        assertTrue(data.contains("First action: call portfolio_overview"));
        assertTrue(data.contains("portfolio_stock_count: 2"));
        assertTrue(data.contains("total_invested: 10000"));
        assertTrue(data.contains(RiskFlag.HIGH_CONCENTRATION.name()));
        assertFalse(data.contains("portfolio_classification:"));
        assertFalse(data.contains("portfolio_overview_json"));
        assertFalse(data.contains("Use the smallest number of tool calls required to produce the final advice."));
    }
}
