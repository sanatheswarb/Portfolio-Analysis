package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.ZerodhaImportResponse;
import com.cursor_springa_ai.playground.integration.zerodha.ZerodhaHoldingsClient;
import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaHoldingItem;
import com.cursor_springa_ai.playground.model.AssetType;
import com.cursor_springa_ai.playground.model.Holding;
import com.cursor_springa_ai.playground.model.Portfolio;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ZerodhaImportService {

    private final ZerodhaHoldingsClient zerodhaHoldingsClient;
    private final PortfolioService portfolioService;
    private final MarketMetricsCache marketMetricsCache;
    private final EnrichedHoldingDataCache enrichedHoldingDataCache;

    public ZerodhaImportService(
            ZerodhaHoldingsClient zerodhaHoldingsClient,
            PortfolioService portfolioService,
            MarketMetricsCache marketMetricsCache,
            EnrichedHoldingDataCache enrichedHoldingDataCache
    ) {
        this.zerodhaHoldingsClient = zerodhaHoldingsClient;
        this.portfolioService = portfolioService;
        this.marketMetricsCache = marketMetricsCache;
        this.enrichedHoldingDataCache = enrichedHoldingDataCache;
    }

    public ZerodhaImportResponse importHoldings(String portfolioId) {
        return importHoldingsWithAutoCreate(portfolioId, null);
    }

    public ZerodhaImportResponse importHoldingsWithAutoCreate(String portfolioId, String ownerName) {
        String resolvedPortfolioId = resolvePortfolioId(portfolioId, ownerName);
        List<ZerodhaHoldingItem> incoming = zerodhaHoldingsClient.fetchHoldings();
        List<String> importedSymbols = new ArrayList<>();
        List<Holding> holdingsToEnrich = new ArrayList<>();

        // Batch fetch and cache market metrics for all holdings at once
        marketMetricsCache.batchFetchAndCache(incoming);

        for (ZerodhaHoldingItem item : incoming) {
            if (item.getTradingSymbol() == null
                    || item.getQuantity() == null
                    || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String symbol = item.getTradingSymbol().toUpperCase(Locale.ROOT);
            String exchange = item.getExchange() != null ? item.getExchange() : "NSE";

            Holding holding = new Holding(
                    symbol,
                    exchange,
                    resolveAssetType(item),
                    item.getQuantity(),
                    item.getAveragePrice() == null ? BigDecimal.ZERO : item.getAveragePrice(),
                    item.getLastPrice() == null ? BigDecimal.ZERO : item.getLastPrice(),
                    item.getPnl() == null ? BigDecimal.ZERO : item.getPnl()
            );

            portfolioService.addOrUpdateHolding(resolvedPortfolioId, holding);
            importedSymbols.add(holding.getSymbol());
            holdingsToEnrich.add(holding);
        }

        // Build and cache enriched holdings for the portfolio
        enrichedHoldingDataCache.buildAndCache(resolvedPortfolioId, holdingsToEnrich);

        return new ZerodhaImportResponse(
                resolvedPortfolioId,
                importedSymbols.size(),
                importedSymbols
        );
    }

    private String resolvePortfolioId(String portfolioId, String ownerName) {
        if (StringUtils.hasText(portfolioId)) {
            return portfolioId;
        }
        if (!StringUtils.hasText(ownerName)) {
            throw new IllegalArgumentException("ownerName is required when portfolioId is not provided");
        }
        Portfolio created = portfolioService.createPortfolio(ownerName.trim());
        return created.getId();
    }

    private AssetType resolveAssetType(ZerodhaHoldingItem item) {
        String symbol = item.getTradingSymbol() == null ? "" : item.getTradingSymbol().toUpperCase(Locale.ROOT);
        if (symbol.endsWith("ETF")) {
            return AssetType.ETF;
        }
        return AssetType.STOCK;
    }
}
