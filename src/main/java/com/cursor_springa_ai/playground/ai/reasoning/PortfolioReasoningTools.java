package com.cursor_springa_ai.playground.ai.reasoning;

import com.cursor_springa_ai.playground.ai.tools.FlaggedHoldingsBuilder;
import com.cursor_springa_ai.playground.ai.tools.HoldingDetailsBuilder;
import com.cursor_springa_ai.playground.ai.tools.HoldingsListBuilder;
import com.cursor_springa_ai.playground.ai.tools.PortfolioOverviewBuilder;
import com.cursor_springa_ai.playground.analytics.model.EnrichedHoldingData;
import com.cursor_springa_ai.playground.util.StringNormalizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final Map<String, EnrichedHoldingData> holdingsBySymbol;
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
        this.holdingsBySymbol = buildHoldingsBySymbol(context.enrichedHoldings());
        this.toolInvocationRecorder = toolInvocationRecorder;
    }

    @Tool(name = "portfolio_overview", description = """
            Purpose: Provides the high-level portfolio big picture including classification, risk level, diversification, concentration, performance, and structure metrics.
            When to use: Use first to assess overall portfolio health, classification, risk drivers, diversification, and structure before giving recommendations.
            When NOT to use: Do not use for individual holding deep dives, fundamental analysis, or detailed stock-by-stock reasoning.
            Returns: Deterministic portfolio-level summary data that is usually sufficient for overall portfolio advice; use flagged_holdings only when deeper risk evidence is needed.
            Constraints: This tool provides factual portfolio data. Do not reinterpret or recompute values. Do not assume missing data. It already summarizes portfolio risks, so avoid calling other tools unless deeper evidence is required.
            """)
    public String portfolioOverview() {
        recordToolInvocation("portfolio_overview");
        if (portfolioOverviewCache != null) {
            return portfolioOverviewCache;
        }
        portfolioOverviewCache = toJson(overviewBuilder.build(context));
        return portfolioOverviewCache;
    }

    @Tool(name = "flagged_holdings", description = """
            Purpose: Returns the small set of holdings that materially drive portfolio risk.
            When to use: Use when you need evidence for risk explanations, justification for recommendations, or identification of major concentration/valuation/diversification risk drivers.
            When NOT to use: Do not use for full portfolio listing, general exploration, or when portfolio_overview already provides sufficient risk explanation.
            Returns: Risk-driving holdings with importance, performance status, valuation, risk severity, and human-readable attention reasons.
            Constraints: This tool provides factual portfolio data. Do not reinterpret or recompute values. Do not assume missing data.
            """)
    public String flaggedHoldings() {
        recordToolInvocation("flagged_holdings");
        if (flaggedHoldingsCache != null) {
            return flaggedHoldingsCache;
        }
        flaggedHoldingsCache = toJson(flaggedHoldingsBuilder.build(context));
        return flaggedHoldingsCache;
    }

    @Tool(name = "holdings_list", description = """
            Purpose: Acts as a lightweight portfolio index for discovery.
            When to use: Use to identify which symbols exist, scan composition, and select holdings for deeper inspection.
            When NOT to use: Do not use for deep analysis, valuation reasoning, or recommendation justification.
            Returns: A lightweight list of all holdings with basic context like allocation, performance status, valuation flag, and risk tags.
            Constraints: This tool provides factual portfolio data. Do not reinterpret or recompute values. Do not assume missing data. Use holding_details after selecting symbols.
            """)
    public String holdingsList() {
        recordToolInvocation("holdings_list");
        if (holdingsListCache != null) {
            return holdingsListCache;
        }
        holdingsListCache = toJson(holdingsListBuilder.build(context));
        return holdingsListCache;
    }

    @Tool(name = "holding_details", description = """
            Purpose: Provides deep reasoning evidence and detailed portfolio plus valuation context for specific holdings.
            When to use: Use for detailed reasoning about selected stocks, valuation context, performance interpretation, and evidence that supports advice.
            When NOT to use: Do not use to explore all holdings, as a first analysis step, or before identifying relevant symbols from portfolio_overview or holdings_list.
            Returns: Structured detailed context per selected symbol including identity, portfolio role, valuation context, performance context, risk context, and signals.
            Constraints: Maximum 5 symbols per call; prioritize the most important holdings. This tool provides factual portfolio data. Do not reinterpret or recompute values. Do not assume missing data.
            """)
    public String holdingDetails(
            @ToolParam(description = "List of stock symbols to inspect (maximum 5), for example [\"INFY\",\"TCS\"]", required = true)
            List<String> symbols
    ) {
        recordToolInvocation("holding_details");
        if (symbols == null || symbols.isEmpty()) {
            return "{\"error\":\"symbols list is required\"}";
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (String symbol : symbols) {
            String normalizedSymbol = StringNormalizer.normalize(symbol);
            if (normalizedSymbol == null) {
                continue;
            }
            if (holdingDetailsCache.containsKey(normalizedSymbol)) {
                try {
                    results.add(objectMapper.readValue(holdingDetailsCache.get(normalizedSymbol), STRING_OBJECT_MAP));
                } catch (Exception e) {
                    results.add(Map.of("error", "failed to deserialize cached details for " + normalizedSymbol));
                }
                continue;
            }
            EnrichedHoldingData holding = holdingsBySymbol.get(normalizedSymbol);
            Map<String, Object> detail = holding != null
                    ? holdingDetailsBuilder.build(holding, context.enrichedHoldings())
                    : Map.of("error", "holding not found for symbol " + normalizedSymbol);
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

    private Map<String, EnrichedHoldingData> buildHoldingsBySymbol(List<EnrichedHoldingData> holdings) {
        Map<String, EnrichedHoldingData> index = new LinkedHashMap<>();
        for (EnrichedHoldingData holding : holdings) {
            String normalizedSymbol = StringNormalizer.normalize(holding.symbol());
            if (normalizedSymbol == null) {
                continue;
            }
            index.putIfAbsent(normalizedSymbol, holding);
        }
        return Map.copyOf(index);
    }
}
