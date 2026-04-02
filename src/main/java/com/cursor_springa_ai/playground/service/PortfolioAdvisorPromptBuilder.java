package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.RiskFlag;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PortfolioAdvisorPromptBuilder {
    private final ObjectMapper objectMapper;

    public PortfolioAdvisorPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String buildSystemPrompt() {
        return """
                You are a conservative Indian equity portfolio advisor.

                PRIMARY OBJECTIVE:
                Protect capital and reduce portfolio risk before optimizing returns.

                PORTFOLIO CLASSIFICATION RULES:
                - Use provided portfolio_classification as the primary interpretation of portfolio structure.
                - Do not attempt to redefine risk level or diversification.
                - Explain risks using the provided classifications.
                - Align suggestions with portfolio_style.

                TOOL RULES:
                - You MUST call portfolio_overview exactly once before producing any final answer.
                - Use tool outputs as the only source of portfolio facts.
                - Do not recompute metrics yourself.
                - Do not invent holding-level details.
                - Do not call portfolio_overview again after its first result.
                - Do not call the same tool repeatedly if you already have its result.
                - Call flagged_holdings only when you need risk evidence for recommendations.
                - Call holding_details only for the few symbols that materially affect the advice.

                PORTFOLIO INTERPRETATION PRIORITY:
                1. Portfolio classification
                2. Risk flags
                3. Portfolio metrics
                4. Holding evidence

                SUGGESTION ALIGNMENT RULES:
                - If portfolio_style is GROWTH_HEAVY, suggestions should focus on risk balancing.
                - If portfolio_style is VALUE_HEAVY, suggestions should focus on diversification.
                - If portfolio_style is MOMENTUM_HEAVY, suggestions should focus on downside protection.
                - If diversification_level is POOR, one suggestion must address diversification.
                - If risk_level is HIGH, first suggestion must reduce concentration risk.

                SUGGESTION STRUCTURE:
                - Suggestion 1: Reduce the biggest identified risk.
                - Suggestion 2: Improve diversification or style balance.
                - Suggestion 3: Improve portfolio resilience.

                RESPONSE RULES:
                - Suggestions must be specific, actionable, and risk focused.
                - If %s exists, the first suggestion must address concentration.
                - If sector concentration exists, one suggestion must address diversification.
                - Avoid generic advice such as monitor market, stay invested, or diversify more.
                - Also identify 1 portfolio strength if present.
                - Return ONLY valid JSON.
                - Do NOT include markdown or commentary outside the JSON object.
                - Include all keys: risk_overview, diversification_feedback, suggestions, cautionary_note.
                - risk_overview, diversification_feedback, and cautionary_note must be non-null strings.
                - risk_overview and diversification_feedback should each be at least one full sentence.
                - suggestions must contain exactly 3 plain-text strings.
                - Do not use colons inside suggestion strings.

                EXPLANATION RULES:
                - Always explain advice using portfolio classification and risk flags.
                - Do not give generic investment advice.
                - Reference concentration, diversification, or style when explaining risks.
                - When explaining risk, state the classification, state the cause, state the implication.

                DO NOT:
                - Suggest buying or selling specific stocks.
                - Provide price targets.
                - Give financial advisory language.
                """.formatted(RiskFlag.HIGH_CONCENTRATION.name());
    }

    public String buildReasoningRequest(PortfolioReasoningContext reasoningContext) {
        return """
                        Portfolio Analysis Request:
                        portfolio_userId: %s
                portfolio_summary:
                - total_invested: %s
                - total_current_value: %s
                - total_pnl: %s
                - total_pnl_percent: %s
                portfolio_classification:
                %s
                        precomputed_portfolio_risk_flags: %s
                        portfolio_stock_count: %s
                First action: call portfolio_overview.
                Do not call portfolio_overview again after the first result.
                Then pull additional tool data only if needed.
                        """
                .formatted(
                        reasoningContext.portfolioUserId(),
                        reasoningContext.portfolioSummary() != null
                                ? reasoningContext.portfolioSummary().totalInvested()
                                : null,
                        reasoningContext.portfolioSummary() != null
                                ? reasoningContext.portfolioSummary().totalCurrentValue()
                                : null,
                        reasoningContext.portfolioSummary() != null
                                ? reasoningContext.portfolioSummary().totalPnL()
                                : null,
                        reasoningContext.portfolioSummary() != null
                                ? reasoningContext.portfolioSummary().totalPnLPercent()
                                : null,
                        buildClassificationBlock(reasoningContext.classification()),
                        portfolioRiskFlags(reasoningContext),
                        reasoningContext.portfolioSummary() != null
                                ? reasoningContext.portfolioSummary().totalHoldings()
                                : 0);
    }

    public String buildRetryReasoningRequest(String baseUserPrompt) {
        return baseUserPrompt + """

                Previous response was truncated.
                Reuse portfolio classification and tool data already obtained.
                Do not repeat analysis steps.
                Return a shorter JSON response.
                Keep risk_overview and diversification_feedback to one concise sentence each.
                Keep each suggestion short and plain text.
                Keep cautionary_note to one short sentence.
                """;
    }

    private String buildClassificationBlock(PortfolioClassification classification) {
        if (classification == null) {
            return """
                    - risk_level: N/A
                    - diversification_level: N/A
                    - concentration_level: N/A
                    - performance_level: N/A
                    - portfolio_style: N/A""";
        }
        return "- risk_level: " + classification.riskLevel() + "\n" +
                "- diversification_level: " + classification.diversificationLevel() + "\n" +
                "- concentration_level: " + classification.concentrationLevel() + "\n" +
                "- performance_level: " + classification.performanceLevel() + "\n" +
                "- portfolio_style: " + classification.portfolioStyle();
    }

    private List<String> portfolioRiskFlags(PortfolioReasoningContext reasoningContext) {
        return reasoningContext.portfolioRiskFlags();
    }

    public String buildEnrichedHoldingsJson(List<EnrichedHoldingData> enrichedHoldings) {
        try {
            List<Map<String, Object>> simplifiedHoldings = enrichedHoldings.stream()
                    .map(holding -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("symbol", holding.symbol());
                        map.put("allocation", holding.allocationPercent() != null
                                ? holding.allocationPercent().setScale(1, java.math.RoundingMode.HALF_UP)
                                : null);
                        map.put("sector", holding.sector());
                        map.put("riskFlags", holding.riskFlags());
                        map.put("profitPercent", holding.profitPercent());
                        return map;
                    })
                    .toList();

            return objectMapper.writeValueAsString(simplifiedHoldings);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize enriched holdings", e);
        }
    }

}
