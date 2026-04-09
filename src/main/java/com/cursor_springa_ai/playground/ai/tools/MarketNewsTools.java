package com.cursor_springa_ai.playground.ai.tools;

import com.cursor_springa_ai.playground.dto.ai.NewsItemDto;
import com.cursor_springa_ai.playground.service.MarketNewsService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class MarketNewsTools {

    private final MarketNewsService newsService;

    public MarketNewsTools(MarketNewsService newsService) {
        this.newsService = newsService;
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
        if (symbol == null) {
            return List.of();
        }
        return newsService.searchStockNews(symbol.toUpperCase(Locale.ROOT));
    }
}
