package com.cursor_springa_ai.playground.ai.advisor;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.analytics.model.PortfolioSummary;
import com.cursor_springa_ai.playground.model.enums.RiskFlag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioAdvisorPromptBuilderTest {

    private final PortfolioAdvisorPromptBuilder builder = new PortfolioAdvisorPromptBuilder(new SuggestionPriorityBuilder());

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
        assertTrue(prompt.contains("Do not produce any portfolio interpretation before reviewing portfolio_overview output."));
        assertTrue(prompt.contains("If largest holding exceeds 25%, one suggestion must address concentration."));
        assertTrue(prompt.contains("If top 3 holdings exceed 60%, one suggestion must address diversification."));
        assertTrue(prompt.contains("When multiple risks exist, prioritize the one affecting the largest allocation."));
        assertTrue(prompt.contains("Reference portfolio metrics when explaining cause."));
        assertTrue(prompt.contains("Do not repeat the same reasoning across multiple sections."));
        assertTrue(prompt.contains("Do not call additional tools to rediscover risks already indicated by portfolio classification."));
        assertTrue(prompt.contains("SUGGESTION PRIORITY RULE"));
        assertTrue(prompt.contains("Suggestions must follow the provided suggestion_priority_order."));
        assertTrue(prompt.contains("Suggestion 1 must address priority 1."));
        assertTrue(prompt.contains("Return exactly this JSON shape and nothing else:"));
        assertTrue(prompt.contains("\"risk_overview\": \"...\""));
        assertTrue(prompt.contains("Do not include any other keys such as signals, rankings, booleans, percentages, metadata, or nested analysis objects."));
        assertTrue(prompt.contains("Keep the full JSON response concise and under 140 words."));
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
            null,
            null
        );

        String data = builder.buildReasoningRequest(reasoningContext);

        assertNotNull(data);
        assertTrue(data.contains("portfolio_userId: portfolio-1"));
        assertTrue(data.contains("First action: call portfolio_overview"));
        assertTrue(data.contains("portfolio_stock_count: 2"));
        assertTrue(data.contains("Use it as the primary portfolio data source."));
        assertTrue(data.contains(RiskFlag.HIGH_CONCENTRATION.name()));
        assertTrue(data.contains("decision_hints:"));
        assertTrue(data.contains("primary_risk:"));
        assertTrue(data.contains("primary_risk_driver:"));
        assertTrue(data.contains("largest_holding_symbol:"));
        assertTrue(data.contains("largest_holding_percent:"));
        assertTrue(data.contains("priority_actions:"));
        assertTrue(data.contains("suggestion_priority_order:"));
        assertTrue(data.contains("Primary focus must be addressing primary_risk."));
        assertFalse(data.contains("portfolio_summary:"));
        assertFalse(data.contains("total_invested:"));
        assertFalse(data.contains("total_current_value:"));
        assertFalse(data.contains("total_pnl:"));
        assertFalse(data.contains("total_pnl_percent:"));
        assertFalse(data.contains("portfolio_classification:"));
        assertFalse(data.contains("portfolio_overview_json"));
        assertFalse(data.contains("Use the smallest number of tool calls required to produce the final advice."));
    }

    @Test
    void buildRetryReasoningRequest_reinforcesExactSchema() {
        String retryPrompt = builder.buildRetryReasoningRequest("base-prompt");

        assertNotNull(retryPrompt);
        assertTrue(retryPrompt.contains("Previous response was truncated or used the wrong schema."));
        assertTrue(retryPrompt.contains("Return only this JSON object and no other keys:"));
        assertTrue(retryPrompt.contains("\"suggestions\": [\"...\", \"...\", \"...\"]"));
        assertTrue(retryPrompt.contains("Do not include keys like signals, portfolio_rankings, concentration_risk, or valuation_gap_percentages."));
        assertTrue(retryPrompt.contains("Stop immediately after the closing brace."));
    }
}
