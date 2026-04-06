package com.cursor_springa_ai.playground.ai.reasoning;

import com.cursor_springa_ai.playground.ai.tools.FlaggedHoldingsBuilder;
import com.cursor_springa_ai.playground.ai.tools.HoldingDetailsBuilder;
import com.cursor_springa_ai.playground.ai.tools.HoldingsListBuilder;
import com.cursor_springa_ai.playground.ai.tools.PortfolioOverviewBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class PortfolioReasoningTools {

    private static final Logger logger = Logger.getLogger(PortfolioReasoningTools.class.getName());
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() { };

    private final PortfolioReasoningContext context;
    private final ObjectMapper objectMapper;
    private final FlaggedHoldingsBuilder flaggedHoldingsBuilder;
    private final HoldingDetailsBuilder holdingDetailsBuilder;
    private final PortfolioOverviewBuilder overviewBuilder;
    private final HoldingsListBuilder holdingsListBuilder;
    private final ToolInvocationRecorder toolInvocationRecorder;
    private final Map<String, Integer> toolInvocationCounts = new LinkedHashMap<>();
    private int invocationCount;
    private String firstInvokedTool;
    private String portfolioOverviewCache;
    private String flaggedHoldingsCache;
    private String holdingsListCache;
    private final Map<String, String> holdingDetailsCache = new LinkedHashMap<>();

    public PortfolioReasoningTools(PortfolioReasoningContext context, ObjectMapper objectMapper) {
        this(context, objectMapper, null);
    }

    public PortfolioReasoningTools(PortfolioReasoningContext context,
                                   ObjectMapper objectMapper,
                                   ToolInvocationRecorder toolInvocationRecorder) {
        this.context = context;
        this.objectMapper = objectMapper;
        this.flaggedHoldingsBuilder = new FlaggedHoldingsBuilder();
        this.holdingDetailsBuilder = new HoldingDetailsBuilder();
        this.overviewBuilder = new PortfolioOverviewBuilder();
        this.holdingsListBuilder = new HoldingsListBuilder();
        this.toolInvocationRecorder = toolInvocationRecorder;
    }

    @Tool(name = "portfolio_overview", description = "Returns deterministic portfolio summary, diversification metrics, sector exposure, portfolio risk flags, and the largest holdings. Call this first.")
    public String portfolioOverview() {
        recordToolInvocation("portfolio_overview");
        if (portfolioOverviewCache != null) {
            return portfolioOverviewCache;
        }
        portfolioOverviewCache = toJson(overviewBuilder.build(context));
        return portfolioOverviewCache;
    }

    @Tool(name = "flagged_holdings", description = "Returns holdings requiring attention, enriched with importance classification, performance status, valuation, risk severity, and human-readable attention reasons. Sorted by allocation descending. Use this for actionable, evidence-backed recommendations.")
    public String flaggedHoldings() {
        recordToolInvocation("flagged_holdings");
        if (flaggedHoldingsCache != null) {
            return flaggedHoldingsCache;
        }
        flaggedHoldingsCache = toJson(flaggedHoldingsBuilder.build(context));
        return flaggedHoldingsCache;
    }

    @Tool(name = "holdings_list", description = "Returns a minimal summary of all holdings: symbol, allocation, PnL, valuation flag, and risk flags. Use this to scan the full portfolio before deciding which symbols to inspect in detail.")
    public String holdingsList() {
        recordToolInvocation("holdings_list");
        if (holdingsListCache != null) {
            return holdingsListCache;
        }
        holdingsListCache = toJson(holdingsListBuilder.build(context));
        return holdingsListCache;
    }

    @Tool(name = "holding_details", description = "Returns structured, context-rich details for the given stock symbols: identity, portfolio role, valuation story, performance story, risk analysis, and human-readable signals. Call this to explain why a stock is risky or important.")
    public String holdingDetails(
            @ToolParam(description = "List of stock symbols to inspect, for example [\"INFY\",\"TCS\"]", required = true)
            List<String> symbols
    ) {
        recordToolInvocation("holding_details");
        if (symbols == null || symbols.isEmpty()) {
            return "{\"error\":\"symbols list is required\"}";
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (String symbol : symbols) {
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
            if (holdingDetailsCache.containsKey(normalizedSymbol)) {
                try {
                    results.add(objectMapper.readValue(holdingDetailsCache.get(normalizedSymbol), STRING_OBJECT_MAP));
                } catch (Exception e) {
                    results.add(Map.of("error", "failed to deserialize cached details for " + normalizedSymbol));
                }
                continue;
            }
            Map<String, Object> detail = context.enrichedHoldings().stream()
                    .filter(holding -> holding.symbol() != null)
                    .filter(holding -> holding.symbol().equalsIgnoreCase(normalizedSymbol))
                    .findFirst()
                    .map(holding -> holdingDetailsBuilder.build(holding, context.enrichedHoldings()))
                    .orElse(Map.of("error", "holding not found for symbol " + normalizedSymbol));
            String detailJson = toJson(detail);
            holdingDetailsCache.put(normalizedSymbol, detailJson);
            results.add(detail);
        }
        return toJson(results);
    }

    public int invocationCount() {
        return invocationCount;
    }

    public Map<String, Integer> invocationCounts() {
        return Map.copyOf(toolInvocationCounts);
    }

    public String firstInvokedTool() {
        return firstInvokedTool;
    }

    public boolean hasInvokedTool(String toolName) {
        return toolInvocationCounts.containsKey(toolName);
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
        invocationCount++;
        if (firstInvokedTool == null) {
            firstInvokedTool = safeToolName;
        }
        if (toolInvocationRecorder != null) {
            toolInvocationRecorder.record(safeToolName);
        }
        logger.info("Advisor tool invoked: " + safeToolName);

        Integer currentCount = toolInvocationCounts.get(safeToolName);
        if (currentCount == null) {
            toolInvocationCounts.put(safeToolName, 1);
            return;
        }
        toolInvocationCounts.put(safeToolName, currentCount + 1);
    }
}
