package com.cursor_springa_ai.playground.ai.reasoning;

import com.cursor_springa_ai.playground.dto.ai.NewsItemDto;
import com.cursor_springa_ai.playground.service.MarketNewsService;
import org.springframework.ai.tool.annotation.Tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class MarketNewsReasoningTools {

    private static final Logger logger = Logger.getLogger(MarketNewsReasoningTools.class.getName());

    private final MarketNewsService newsService;
    private final ToolInvocationRecorder toolInvocationRecorder;
    private final Map<String, Integer> toolInvocationCounts = new LinkedHashMap<>();
    private final Map<String, List<NewsItemDto>> newsBySymbolCache = new LinkedHashMap<>();
    private int invocationCount;
    private String firstInvokedTool;

    public MarketNewsReasoningTools(MarketNewsService newsService) {
        this(newsService, null);
    }

    public MarketNewsReasoningTools(MarketNewsService newsService,
                                    ToolInvocationRecorder toolInvocationRecorder) {
        this.newsService = newsService;
        this.toolInvocationRecorder = toolInvocationRecorder;
    }

    @Tool(
            name = "search_stock_news",
            description = """
                    Fetch recent financial news related to a stock.
                    Use only when user asks about recent news, company events, or market developments.
                    Returns up to 5 recent items with structured signals: impact, materiality, and riskRelevant.
                    Use these fields to decide whether news should influence risk explanation.
                    """
    )
    public List<NewsItemDto> searchStockNews(String symbol) {
        recordToolInvocation("search_stock_news");
        if (symbol == null || symbol.isBlank()) {
            return List.of();
        }

        String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        List<NewsItemDto> cachedNews = newsBySymbolCache.get(normalizedSymbol);
        if (cachedNews != null) {
            return cachedNews;
        }

        List<NewsItemDto> news = newsService.searchStockNews(normalizedSymbol);
        List<NewsItemDto> immutableNews = news == null ? List.of() : List.copyOf(news);
        newsBySymbolCache.put(normalizedSymbol, immutableNews);
        return immutableNews;
    }

    public int invocationCount() {
        return invocationCount;
    }

    public Map<String, Integer> invocationCounts() {
        return Map.copyOf(toolInvocationCounts);
    }

    public boolean hasInvokedTool(String toolName) {
        return toolInvocationCounts.containsKey(toolName);
    }

    public String firstInvokedTool() {
        return firstInvokedTool;
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
        logger.info("Market news tool invoked: " + safeToolName);

        Integer currentCount = toolInvocationCounts.get(safeToolName);
        if (currentCount == null) {
            toolInvocationCounts.put(safeToolName, 1);
            return;
        }
        toolInvocationCounts.put(safeToolName, currentCount + 1);
    }
}