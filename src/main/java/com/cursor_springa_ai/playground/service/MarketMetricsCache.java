package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.StockMetrics;
import com.cursor_springa_ai.playground.integration.market.NseApiClient;
import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaHoldingItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Service
public class MarketMetricsCache {

    private static final Logger logger = Logger.getLogger(MarketMetricsCache.class.getName());
    private final ConcurrentHashMap<String, StockMetrics> metricsCache = new ConcurrentHashMap<>();
    private final NseApiClient nseApiClient;

    public MarketMetricsCache(NseApiClient nseApiClient) {
        this.nseApiClient = nseApiClient;
    }

    /**
     * Batch fetch and cache metrics for multiple holdings.
     * Fetches only uncached symbols and updates cache with results.
     */
    public void batchFetchAndCache(List<ZerodhaHoldingItem> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return;
        }

        // Filter only uncached holdings
        List<ZerodhaHoldingItem> uncachedHoldings = new ArrayList<>();
        for (ZerodhaHoldingItem holding : holdings) {
            String symbol = holding.getTradingSymbol();
            if (symbol != null && !metricsCache.containsKey(symbol.toUpperCase())) {
                uncachedHoldings.add(holding);
            }
        }

        if (uncachedHoldings.isEmpty()) {
            return; // All symbols already cached
        }

        // Batch fetch from NSE API
        Map<String, StockMetrics> fetchedMetrics = nseApiClient.fetchMetricsForHoldings(uncachedHoldings);

        // Update cache
        for (Map.Entry<String, StockMetrics> entry : fetchedMetrics.entrySet()) {
            metricsCache.put(entry.getKey().toUpperCase(), entry.getValue());
            logger.info("Cached metric for symbol: " + entry.getKey() + " | PE: " + entry.getValue().pe() +
                    ", Sector: " + entry.getValue().sector());
        }
        logger.info("Batch cache update completed. Total cached symbols: " + metricsCache.size());
    }

    /**
     * Get metrics for a symbol, fetching from NSE if not in cache.
     */
    public StockMetrics getMetrics(String symbol) {
        return metricsCache.computeIfAbsent(symbol.toUpperCase(), key -> nseApiClient.fetchMetricsForSymbol(key));
    }

    /**
     * Store metrics in cache.
     */
    public void cacheMetrics(String symbol, StockMetrics metrics) {
        if (metrics != null) {
            metricsCache.put(symbol.toUpperCase(), metrics);
            logger.info("Cached metric for symbol: " + symbol.toUpperCase() + " | PE: " + metrics.pe() +
                    ", Beta: " + metrics.beta() + ", Week52High: " + metrics.week52High());
        }
    }

    /**
     * Clear cache (useful for testing or refresh).
     */
    public void clearCache() {
        metricsCache.clear();
    }

    /**
     * Get all cached metrics.
     */
    public ConcurrentHashMap<String, StockMetrics> getAllMetrics() {
        return new ConcurrentHashMap<>(metricsCache);
    }
}
