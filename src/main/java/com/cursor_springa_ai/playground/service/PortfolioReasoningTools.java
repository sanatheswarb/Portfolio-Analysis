package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PortfolioReasoningTools {

    private final PortfolioReasoningContext context;
    private final ObjectMapper objectMapper;

    public PortfolioReasoningTools(PortfolioReasoningContext context, ObjectMapper objectMapper) {
        this.context = context;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "portfolio_overview", description = "Returns deterministic portfolio summary, diversification metrics, sector exposure, portfolio risk flags, and the largest holdings. Call this first.")
    public String portfolioOverview() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("portfolioUserId", context.portfolioUserId());
        payload.put("summary", context.portfolioSummary());
        payload.put("metrics", portfolioStatsPayload());
        payload.put("portfolioRiskFlags", context.portfolioRiskFlags());
        payload.put("largestHoldings", largestHoldings());
        return toJson(payload);
    }

    @Tool(name = "portfolio_metrics", description = "Returns deterministic precomputed portfolio-level metrics such as concentration, sector exposure, portfolio risk flags, and diversification score for the active analysis context.")
    public String portfolioMetrics() {
        if (context.portfolioMetrics() == null) {
            return "{\"error\":\"portfolio metrics are unavailable\"}";
        }
        return toJson(context.portfolioMetrics());
    }
    @Tool(name = "flagged_holdings", description = "Returns only holdings that already have deterministic risk flags, sorted by allocation descending. Use this when you need evidence for recommendations.")
    public String flaggedHoldings() {
        List<Map<String, Object>> holdings = context.enrichedHoldings().stream()
                .filter(holding -> holding.riskFlags() != null && !holding.riskFlags().isEmpty())
                .sorted((left, right) -> compareByAllocation(right, left))
                .map(this::toHoldingSummary)
                .toList();
        return toJson(holdings);
    }

    @Tool(name = "holding_details", description = "Returns deterministic metrics and risk flags for a single holding symbol when you need supporting evidence for advice.")
    public String holdingDetails(
            @ToolParam(description = "Stock symbol to inspect, for example INFY or TCS", required = true)
            String symbol
    ) {
        if (symbol == null || symbol.isBlank()) {
            return "{\"error\":\"symbol is required\"}";
        }

        return context.enrichedHoldings().stream()
                .filter(holding -> holding.symbol() != null)
                .filter(holding -> holding.symbol().equalsIgnoreCase(symbol.trim()))
                .findFirst()
                .map(this::toHoldingSummary)
                .map(this::toJson)
                .orElse("{\"error\":\"holding not found for symbol " + symbol.trim().toUpperCase(Locale.ROOT) + "\"}");
    }

    private List<Map<String, Object>> largestHoldings() {
        return context.enrichedHoldings().stream()
                .sorted((left, right) -> compareByAllocation(right, left))
                .limit(5)
                .map(this::toHoldingSummary)
                .toList();
    }

    private Map<String, Object> toHoldingSummary(EnrichedHoldingData holding) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("symbol", holding.symbol());
        payload.put("sector", holding.sector());
        payload.put("allocationPercent", holding.allocationPercent());
        payload.put("profitPercent", holding.profitPercent());
        payload.put("distanceFromHigh", holding.distanceFromHigh());
        payload.put("marketCapType", holding.marketCapType());
        payload.put("riskFlags", holding.riskFlags());
        return payload;
    }

    private Map<String, Object> portfolioStatsPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (context.portfolioStats() == null) {
            return payload;
        }

        payload.put("totalInvested", context.portfolioStats().getTotalInvested());
        payload.put("totalValue", context.portfolioStats().getTotalValue());
        payload.put("totalPnl", context.portfolioStats().getTotalPnl());
        payload.put("pnlPercent", context.portfolioStats().getPnlPercent());
        payload.put("largestWeight", context.portfolioStats().getLargestWeight());
        payload.put("stockCount", context.portfolioStats().getStockCount());
        payload.put("dayChange", context.portfolioStats().getDayChange());
        payload.put("dayChangePercent", context.portfolioStats().getDayChangePercent());
        payload.put("top3HoldingPercent", context.portfolioStats().getTop3HoldingPercent());
        payload.put("diversificationScore", context.portfolioStats().getDiversificationScore());
        payload.put("calculatedAt", context.portfolioStats().getCalculatedAt() != null
                ? context.portfolioStats().getCalculatedAt().toString()
                : null);
        return payload;
    }

    private int compareByAllocation(EnrichedHoldingData left, EnrichedHoldingData right) {
        if (left.allocationPercent() == null && right.allocationPercent() == null) {
            return 0;
        }
        if (left.allocationPercent() == null) {
            return -1;
        }
        if (right.allocationPercent() == null) {
            return 1;
        }
        return left.allocationPercent().compareTo(right.allocationPercent());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize reasoning tool payload", exception);
        }
    }
}
