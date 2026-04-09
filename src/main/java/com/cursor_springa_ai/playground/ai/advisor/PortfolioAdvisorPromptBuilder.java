package com.cursor_springa_ai.playground.ai.advisor;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.dto.ai.PortfolioDecisionHints;
import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.model.RiskFlag;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Component
public class PortfolioAdvisorPromptBuilder {

    private final SuggestionPriorityBuilder suggestionPriorityBuilder;

    public PortfolioAdvisorPromptBuilder(SuggestionPriorityBuilder suggestionPriorityBuilder) {
        this.suggestionPriorityBuilder = suggestionPriorityBuilder;
    }

    public String buildSystemPrompt() {
        return String.join(
                "\n\n",
                buildRoleSection(),
                buildToolRules(),
                """
                        PORTFOLIO CLASSIFICATION RULES:
                        - Use provided portfolio_classification as the primary interpretation of portfolio structure.
                        - Do not attempt to redefine risk level or diversification.
                        - Explain risks using the provided classifications.
                        - Align suggestions with portfolio_style.
                        - Do not call additional tools to rediscover risks already indicated by portfolio classification.
                        - Explain classified risks rather than rediscovering them.

                        PORTFOLIO INTERPRETATION PRIORITY:
                        1. Portfolio classification
                        2. Risk flags
                        3. Portfolio metrics
                        4. Holding evidence
                        """,
                buildSuggestionRules(),
                buildOutputRules().formatted(RiskFlag.HIGH_CONCENTRATION.name()));
    }

    private String buildRoleSection() {
        return """
                You are a conservative Indian equity portfolio advisor.

                PRIMARY OBJECTIVE:
                Protect capital and reduce portfolio risk before optimizing returns.
                """;
    }

    private String buildToolRules() {
        return """
                TOOL RULES:
                - You MUST call portfolio_overview exactly once before producing any final answer.
                - Do not produce any portfolio interpretation before reviewing portfolio_overview output.
                - Use tool outputs as the only source of portfolio facts.
                - Do not recompute metrics yourself.
                - Do not invent holding-level details.
                - Do not call portfolio_overview again after its first result.
                - Do not call the same tool repeatedly if you already have its result.
                - Stop tool usage once sufficient evidence is gathered.
                - Do not gather unnecessary data.
                - Call flagged_holdings only when you need risk evidence for recommendations.
                - Call holdings_list to scan all holdings and identify which symbols are relevant.
                - Call holding_details only for the few symbols that materially affect the advice.
                """;
    }

    private String buildSuggestionRules() {
        return """
                SUGGESTION ALIGNMENT RULES:
                - If portfolio_style is GROWTH_HEAVY, suggestions should focus on risk balancing.
                - If portfolio_style is VALUE_HEAVY, suggestions should focus on diversification.
                - If portfolio_style is MOMENTUM_HEAVY, suggestions should focus on downside protection.
                - If diversification_level is POOR, one suggestion must address diversification.
                - If largest holding exceeds 25%, one suggestion must address concentration.
                - If top 3 holdings exceed 60%, one suggestion must address diversification.
                - If risk_level is HIGH, first suggestion must reduce concentration risk.
                - When multiple risks exist, prioritize the one affecting the largest allocation.

                SUGGESTION STRUCTURE:
                - Suggestion 1: Reduce the biggest identified risk.
                - Suggestion 2: Improve diversification or style balance.
                - Suggestion 3: Improve portfolio resilience.

                SUGGESTION PRIORITY RULE:
                - Suggestions must follow the provided suggestion_priority_order.
                - Suggestion 1 must address priority 1.
                - Suggestion 2 must address priority 2.
                - Suggestion 3 must address priority 3.
                """;
    }

    private String buildOutputRules() {
        return """
                RESPONSE RULES:
                - Suggestions must be specific, actionable, and risk focused.
                - If %s exists, the first suggestion must address concentration.
                - If sector concentration exists, one suggestion must address diversification.
                - Avoid generic advice such as monitor market, stay invested, or diversify more.
                - Identify one meaningful portfolio strength that offsets a major risk if possible.
                - Return ONLY valid JSON.
                - Do NOT include markdown or commentary outside the JSON object.
                                - Return exactly this JSON shape and nothing else:
                                    {
                                        "risk_overview": "...",
                                        "diversification_feedback": "...",
                                        "suggestions": ["...", "...", "..."],
                                        "cautionary_note": "..."
                                    }
                - Include all keys: risk_overview, diversification_feedback, suggestions, cautionary_note.
                                - Do not include any other keys such as signals, rankings, booleans, percentages, metadata, or nested analysis objects.
                                - Do not return arrays or nested objects except the suggestions array.
                - risk_overview, diversification_feedback, and cautionary_note must be non-null strings.
                - risk_overview and diversification_feedback should each be at least one full sentence.
                - suggestions must contain exactly 3 plain-text strings.
                - Do not use colons inside suggestion strings.
                                - Keep the full JSON response concise and under 140 words.
                                - End the response immediately after the closing brace.

                EXPLANATION RULES:
                - Always explain advice using portfolio classification and risk flags.
                - Do not give generic investment advice.
                - Reference concentration, diversification, or style when explaining risks.
                - When explaining risk, state the classification, state the cause, state the implication.
                - Reference portfolio metrics when explaining cause.
                - Do not repeat the same reasoning across multiple sections.
                - Each section should provide new insight.

                DO NOT:
                - Suggest buying or selling specific stocks.
                - Provide price targets.
                - Give financial advisory language.
                """;
    }

    public String buildReasoningRequest(PortfolioReasoningContext reasoningContext) {
        DecisionHints hints = buildDecisionHints(reasoningContext);
        List<String> priorities = suggestionPriorityBuilder.build(reasoningContext);
        String priorityOrder = formatPriorityOrder(priorities);

        return """
                        Portfolio Analysis Request:
                        portfolio_userId: %s
                        precomputed_portfolio_risk_flags: %s
                        portfolio_stock_count: %s
                        decision_hints:
                        - primary_risk: %s
                        - primary_risk_driver: %s
                        - largest_holding_symbol: %s
                        - largest_holding_percent: %s%s
                        - priority_actions: %s
                        suggestion_priority_order:
                %s
                Primary focus must be addressing primary_risk.
                First action: call portfolio_overview.
                Use it as the primary portfolio data source.
                Do not call portfolio_overview again after the first result.
                Pull additional tool data only when needed to support specific risk evidence in suggestions.
                        """
                .formatted(
                        reasoningContext.portfolioUserId(),
                        portfolioRiskFlags(reasoningContext),
                        reasoningContext.portfolioSummary() != null
                                ? reasoningContext.portfolioSummary().totalHoldings()
                                : 0,
                        hints.primaryRisk(),
                        hints.primaryRiskDriver(),
                        hints.largestHoldingSymbol(),
                        hints.largestHoldingPercent(),
                        largestHoldingThresholdNote(hints.largestHoldingPercent()),
                        hints.priorityActions(),
                        priorityOrder);
    }

    private DecisionHints buildDecisionHints(PortfolioReasoningContext context) {
        PortfolioDecisionHints existing = context.decisionHints();
        List<String> priorityActions = suggestionPriorityBuilder.build(context);

        EnrichedHoldingData largest = context.enrichedHoldings().stream()
                .filter(h -> h.allocationPercent() != null)
                .max(Comparator.comparing(EnrichedHoldingData::allocationPercent))
                .orElse(null);

        String primaryRisk = existing != null ? existing.primaryRisk() : null;
        String largestHoldingSymbol = existing != null ? existing.largestHoldingSymbol()
                : (largest != null ? largest.symbol() : null);
        BigDecimal largestHoldingPercent = existing != null ? existing.largestHoldingPercent()
                : (largest != null ? largest.allocationPercent() : null);
        // The largest holding is the primary driver of concentration risk
        String primaryRiskDriver = largestHoldingSymbol;

        return new DecisionHints(
                primaryRisk,
                primaryRiskDriver,
                largestHoldingSymbol,
                largestHoldingPercent,
                priorityActions
        );
    }

    private String largestHoldingThresholdNote(BigDecimal largestHoldingPercent) {
        if (largestHoldingPercent != null && largestHoldingPercent.compareTo(BigDecimal.valueOf(25)) > 0) {
            return " (exceeds 25% safe diversification limit)";
        }
        return "";
    }

    private String formatPriorityOrder(List<String> priorities) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < priorities.size(); i++) {
            sb.append("        ").append(i + 1).append(" ").append(priorities.get(i)).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    public String buildRetryReasoningRequest(String baseUserPrompt) {
        return baseUserPrompt + """

                                Previous response was truncated or used the wrong schema.
                Reuse portfolio classification and tool data already obtained.
                Do not call tools again unless missing required data.
                Do not repeat analysis steps.
                                Return only this JSON object and no other keys:
                                {
                                    "risk_overview": "...",
                                    "diversification_feedback": "...",
                                    "suggestions": ["...", "...", "..."],
                                    "cautionary_note": "..."
                                }
                                Do not include keys like signals, portfolio_rankings, concentration_risk, or valuation_gap_percentages.
                                Return a shorter JSON response.
                Keep risk_overview and diversification_feedback to one concise sentence each.
                Keep each suggestion short and plain text.
                Keep cautionary_note to one short sentence.
                                Stop immediately after the closing brace.
                """;
    }

    private List<String> portfolioRiskFlags(PortfolioReasoningContext reasoningContext) {
        return reasoningContext.portfolioRiskFlags();
    }
}
