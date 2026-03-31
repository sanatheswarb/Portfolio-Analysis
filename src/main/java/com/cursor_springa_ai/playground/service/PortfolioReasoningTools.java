package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class PortfolioReasoningTools {

    private static final Logger logger = Logger.getLogger(PortfolioReasoningTools.class.getName());

    private final PortfolioReasoningContext context;
    private final ObjectMapper objectMapper;
    private final List<String> toolInvocationOrder = new ArrayList<>();
    private final Map<String, Integer> toolInvocationCounts = new LinkedHashMap<>();
    private String portfolioOverviewCache;
    private String flaggedHoldingsCache;
    private final Map<String, String> holdingDetailsCache = new LinkedHashMap<>();

    public PortfolioReasoningTools(PortfolioReasoningContext context, ObjectMapper objectMapper) {
        this.context = context;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "portfolio_overview", description = "Returns deterministic portfolio summary, diversification metrics, sector exposure, portfolio risk flags, and the largest holdings. Call this first.")
    public String portfolioOverview() {
        recordToolInvocation("portfolio_overview");
        if (portfolioOverviewCache != null) {
            return portfolioOverviewCache;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("portfolioUserId", context.portfolioUserId());
        payload.put("summary", context.portfolioSummary());
        payload.put("metrics", portfolioStatsPayload());
        payload.put("sectorExposure", sectorExposure());
        payload.put("portfolioRiskFlags", context.portfolioRiskFlags());
        payload.put("largestHoldings", largestHoldings());
        portfolioOverviewCache = toJson(payload);
        return portfolioOverviewCache;
    }

    @Tool(name = "flagged_holdings", description = "Returns only holdings that already have deterministic risk flags, sorted by allocation descending. Use this when you need evidence for recommendations.")
    public String flaggedHoldings() {
        recordToolInvocation("flagged_holdings");
        if (flaggedHoldingsCache != null) {
            return flaggedHoldingsCache;
        }
        List<Map<String, Object>> holdings = context.enrichedHoldings().stream()
                .filter(holding -> holding.riskFlags() != null && !holding.riskFlags().isEmpty())
                .sorted((left, right) -> compareByAllocation(right, left))
            .limit(8)
                .map(this::toHoldingSummary)
                .toList();
        flaggedHoldingsCache = toJson(holdings);
        return flaggedHoldingsCache;
    }

    @Tool(name = "holding_details", description = "Returns deterministic metrics and risk flags for a single holding symbol when you need supporting evidence for advice.")
    public String holdingDetails(
            @ToolParam(description = "Stock symbol to inspect, for example INFY or TCS", required = true)
            String symbol
    ) {
        recordToolInvocation("holding_details");
        logger.info("Advisor tool invoked: holding_details symbol=" + symbol);
        if (symbol == null || symbol.isBlank()) {
            return "{\"error\":\"symbol is required\"}";
        }

        String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        if (holdingDetailsCache.containsKey(normalizedSymbol)) {
            return holdingDetailsCache.get(normalizedSymbol);
        }

        String payload = context.enrichedHoldings().stream()
                .filter(holding -> holding.symbol() != null)
                .filter(holding -> holding.symbol().equalsIgnoreCase(normalizedSymbol))
                .findFirst()
                .map(this::toHoldingSummary)
                .map(this::toJson)
                .orElse("{\"error\":\"holding not found for symbol " + normalizedSymbol + "\"}");
        holdingDetailsCache.put(normalizedSymbol, payload);
        return payload;
    }

    public int invocationCount() {
        return toolInvocationOrder.size();
    }

    public Map<String, Integer> invocationCounts() {
        return Map.copyOf(toolInvocationCounts);
    }

    public String firstInvokedTool() {
        return toolInvocationOrder.isEmpty() ? null : toolInvocationOrder.getFirst();
    }

    public boolean hasInvokedTool(String toolName) {
        return toolInvocationCounts.containsKey(toolName);
    }

    private List<Map<String, Object>> largestHoldings() {
        return context.enrichedHoldings().stream()
                .sorted((left, right) -> compareByAllocation(right, left))
                .limit(3)
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

        payload.put("largestWeight", context.portfolioStats().getLargestWeight());
        payload.put("dayChangePercent", context.portfolioStats().getDayChangePercent());
        payload.put("top3HoldingPercent", context.portfolioStats().getTop3HoldingPercent());
        payload.put("diversificationScore", context.portfolioStats().getDiversificationScore());
        return payload;
    }

    private Map<String, Object> sectorExposure() {
        Map<String, BigDecimal> exposure = new LinkedHashMap<>();
        for (EnrichedHoldingData holding : context.enrichedHoldings()) {
            String sector = holding.sector();
            if (sector == null || sector.isBlank()) {
                sector = "UNKNOWN";
            }
            BigDecimal allocationPercent = holding.allocationPercent() != null
                    ? holding.allocationPercent()
                    : BigDecimal.ZERO;
            exposure.merge(sector, allocationPercent, BigDecimal::add);
        }
        return exposure;
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

    private void recordToolInvocation(String toolName) {
        String safeToolName = Objects.requireNonNull(toolName, "toolName must not be null");
        toolInvocationOrder.add(safeToolName);
        logger.info("Advisor tool invoked: " + safeToolName);

        Integer currentCount = toolInvocationCounts.get(safeToolName);
        if (currentCount == null) {
            toolInvocationCounts.put(safeToolName, 1);
            return;
        }
        toolInvocationCounts.put(safeToolName, currentCount + 1);
    }

}
