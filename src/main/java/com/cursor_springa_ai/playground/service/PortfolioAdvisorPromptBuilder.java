package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioMetrics;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Component
public class PortfolioAdvisorPromptBuilder {

    private static final Logger logger = Logger.getLogger(PortfolioAdvisorPromptBuilder.class.getName());
    private final ObjectMapper objectMapper;

    public PortfolioAdvisorPromptBuilder() {
        this.objectMapper = new ObjectMapper();
    }

    public String buildSystemPrompt() {
        return """
                        You are a conservative Indian equity portfolio advisor.

                        PRIMARY OBJECTIVE:
                        Protect capital and reduce portfolio risk before optimizing returns.

                        TOOL USAGE RULES:
                        - Call portfolio_overview first.
                        - Use flagged_holdings only when you need supporting evidence for a risk-focused recommendation.
                        - Use holding_details only for a specific symbol when the overview is not enough.
                        - Treat tool outputs as the source of truth for all metrics and holding evidence.
                        - Do not recompute metrics yourself.

                        ANALYSIS PRIORITY:
                        1. Portfolio risk flags and concentration
                        2. Diversification quality
                        3. Holding-level supporting evidence
                        4. Return optimization

                        SUGGESTION RULES:
                        - Suggestions must be specific, actionable, and risk focused.
                        - If HIGH_CONCENTRATION exists, the first suggestion must address concentration.
                        - If sector concentration exists, one suggestion must address diversification.
                        - Avoid generic advice such as monitor market, stay invested, or diversify more.

                        OUTPUT REQUIREMENTS:
                        - Return ONLY valid JSON.
                        - Do NOT include markdown or commentary outside the JSON object.
                        - suggestions must contain exactly 3 plain-text strings.
                        - Do not use colons inside suggestion strings.
                        - Base every conclusion on the supplied tool outputs only.

                        """;
    }

    public String buildReasoningRequest(PortfolioReasoningContext reasoningContext) {
        String summaryJson = buildPortfolioSummaryJson(reasoningContext.portfolioSummary());

        return """
                        Portfolio Analysis Request:
                        portfolio_id: %s
                        owner_name: %s
                        portfolio_summary: %s
                        precomputed_portfolio_risk_flags: %s
                        use_tools_for_metrics_and_holding_evidence: true
                        """
                .formatted(
                        reasoningContext.portfolioId(),
                        reasoningContext.ownerName(),
                        summaryJson,
                        reasoningContext.portfolioMetrics() == null ? List.of() : reasoningContext.portfolioMetrics().portfolioRiskFlags()
                );
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

            String json = objectMapper.writeValueAsString(simplifiedHoldings);
            logger.info("Built enriched holdings JSON length=" + json.length());
            return json;
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize enriched holdings", e);
        }
    }

    public String buildPortfolioSummaryJson(PortfolioSummary portfolioSummary) {
        try {
            String json = objectMapper.writeValueAsString(portfolioSummary);
            logger.info("Built portfolio summary JSON length=" + json.length());
            return json;
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize portfolio summary", e);
        }
    }

    public String buildPortfolioMetricsJson(PortfolioMetrics portfolioMetrics) {
        try {
            String json = objectMapper.writeValueAsString(portfolioMetrics);
            logger.info("Built portfolio metrics JSON length=" + json.length());
            return json;
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize portfolio metrics", e);
        }
    }
}
