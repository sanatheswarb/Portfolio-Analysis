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

                        ANALYSIS PRIORITY ORDER:
                        1 Portfolio risk signals (highest importance)
                        2 Portfolio diversification metrics
                        3 Holding level risks
                        4 Profit opportunities (lowest importance)

                        Always prioritize risk reduction over return maximization.

                        --------------------------------

                        ANALYSIS RULES:

                        Always evaluate:

                        CONCENTRATION RISK:
                        Top holding >25% = high concentration risk
                        Top 3 holdings >60% = portfolio concentration risk

                        DIVERSIFICATION:
                        Diversification score >75 = good
                        Diversification score 50–75 = moderate
                        Diversification score <50 = weak diversification

                        Sector exposure >40% = sector concentration risk.

                        VALUATION RISK:
                        Stock PE significantly higher than sector PE indicates valuation risk.

                        DRAWDOWN RISK:
                        Stocks more than 25% below 52 week high indicate weakness or correction risk.

                        SMALLCAP RISK:
                        High smallcap exposure increases volatility risk.

                        PROFIT CONCENTRATION:
                        Large profit from few holdings increases reversal risk.

                        --------------------------------

                        INTERPRETATION RULES:

                        Portfolio_metrics represent portfolio structure.
                        Holdings provide supporting evidence.

                        Base conclusions primarily on portfolio_metrics.
                        Use holdings only to justify observations.

                        If portfolio risk flags exist, prioritize addressing them in suggestions.

                        Do not repeat raw numbers unless necessary.

                        Focus on insights, not data repetition.

                        --------------------------------

                        SUGGESTION RULES:

                        Suggestions must be:

                        Specific
                        Actionable
                        Risk focused
                        Based on provided data

                        Allowed suggestion types:

                        Reduce concentration of oversized positions
                        Rebalance sector allocation
                        Trim overvalued holdings
                        Improve diversification
                        Reduce volatility exposure
                        Protect profits in overheated positions
                        Reduce exposure to weak trend stocks

                        Avoid generic advice like:

                        "Monitor market"
                        "Keep watching"
                        "Diversify more"
                        "Stay invested"

                        Bad suggestions must not be generated.

                        If HIGH_CONCENTRATION exists:
                        First suggestion must address concentration.

                        If SECTOR_CONCENTRATION exists:
                        One suggestion must address diversification.

                        --------------------------------

                        OUTPUT REQUIREMENTS:

                        Return ONLY valid JSON.

                        Do NOT include markdown.
                        Do NOT include explanations.
                        Do NOT include text before or after JSON.

                        Return exactly this format:

                        {
                        "risk_overview":"",
                        "diversification_feedback":"",
                        "suggestions":[
                        "",
                        "",
                        ""
                        ],
                        "cautionary_note":""
                        }

                        CRITICAL JSON FORMATTING RULES:

                        - suggestions MUST be an array of exactly 3 strings
                        - Each string in suggestions MUST be plain text without quotes inside
                        - Do NOT add keys, objects, or metadata inside the suggestions array
                        - Do NOT use colons (:) inside suggestion strings
                        - Each suggestion must be a complete sentence starting with an action verb

                        Ensure:

                        risk_overview:
                        1–2 sentences summarizing major portfolio risks.

                        diversification_feedback:
                        1–2 sentences about diversification quality.

                        suggestions:
                        Exactly 3 actionable improvements.

                        cautionary_note:
                        One critical vulnerability.

                        All values must be strings.

                        --------------------------------

                        FINAL RULE:

                        Base advice strictly on provided portfolio data.
                        Do not assume missing information.
                        Do not hallucinate macro events.
                        If no major risks exist, focus suggestions on optimization.

                        """;
    }

    public String buildPortfolioDataWithMetrics(
            com.cursor_springa_ai.playground.model.Portfolio portfolio,
            List<EnrichedHoldingData> enrichedHoldings,
            PortfolioMetrics portfolioMetrics,
            PortfolioSummary portfolioSummary) {

        String holdingsJson = buildEnrichedHoldingsJson(enrichedHoldings);
        String metricsJson = buildPortfolioMetricsJson(portfolioMetrics);
        String summaryJson = buildPortfolioSummaryJson(portfolioSummary);

        return """
                        Portfolio Analysis Request:


                        Portfolio Summary:
                        %s

                        Portfolio Metrics:
                        %s

                        Holdings (enriched with market metrics and risk flags):
                        %s

                        """
                .formatted(summaryJson, metricsJson, holdingsJson);
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