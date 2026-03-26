package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.StockMetrics;
import com.cursor_springa_ai.playground.integration.market.NseApiClient;
import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaHoldingItem;
import com.cursor_springa_ai.playground.model.NseData;
import com.cursor_springa_ai.playground.repository.NseDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional
public class MarketMetricsCache {

    private static final Logger logger = Logger.getLogger(MarketMetricsCache.class.getName());

    private final NseApiClient nseApiClient;
    private final NseDataRepository nseDataRepository;

    public MarketMetricsCache(NseApiClient nseApiClient, NseDataRepository nseDataRepository) {
        this.nseApiClient = nseApiClient;
        this.nseDataRepository = nseDataRepository;
    }

    /**
     * Batch fetch and cache metrics for multiple holdings.
     * Fetches only symbols not already stored in the database.
     */
    public void batchFetchAndCache(List<ZerodhaHoldingItem> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return;
        }

        List<String> allSymbols = holdings.stream()
                .filter(h -> h.getTradingSymbol() != null)
                .map(h -> h.getTradingSymbol().toUpperCase())
                .distinct()
                .collect(Collectors.toList());

        Set<String> existingSymbols = nseDataRepository.findBySymbolIn(allSymbols)
                .stream()
                .map(NseData::getSymbol)
                .collect(Collectors.toSet());

        List<ZerodhaHoldingItem> uncachedHoldings = holdings.stream()
                .filter(h -> h.getTradingSymbol() != null
                        && !existingSymbols.contains(h.getTradingSymbol().toUpperCase()))
                .collect(Collectors.toList());

        if (uncachedHoldings.isEmpty()) {
            return;
        }

        Map<String, StockMetrics> fetchedMetrics = nseApiClient.fetchMetricsForHoldings(uncachedHoldings);

        for (Map.Entry<String, StockMetrics> entry : fetchedMetrics.entrySet()) {
            String symbol = entry.getKey().toUpperCase();
            NseData nseData = NseData.fromStockMetrics(symbol, entry.getValue());
            nseDataRepository.save(nseData);
            logger.info("Stored NSE data for symbol: " + symbol + " | PE: " + entry.getValue().pe() +
                    ", Sector: " + entry.getValue().sector());
        }
        logger.info("Batch NSE data update completed. Total stored: " + nseDataRepository.count());
    }

    /**
     * Get metrics for a symbol, fetching from NSE if not stored in the database.
     */
    public StockMetrics getMetrics(String symbol) {
        String upperSymbol = symbol.toUpperCase();
        return nseDataRepository.findById(upperSymbol)
                .map(NseData::toStockMetrics)
                .orElseGet(() -> {
                    StockMetrics fetched = nseApiClient.fetchMetricsForSymbol(upperSymbol);
                    if (fetched != null) {
                        nseDataRepository.save(NseData.fromStockMetrics(upperSymbol, fetched));
                        logger.info("Fetched and stored NSE data for symbol: " + upperSymbol);
                    }
                    return fetched;
                });
    }

    /**
     * Store metrics in the database.
     */
    public void cacheMetrics(String symbol, StockMetrics metrics) {
        if (metrics != null) {
            String upperSymbol = symbol.toUpperCase();
            NseData nseData = nseDataRepository.findById(upperSymbol)
                    .orElse(NseData.fromStockMetrics(upperSymbol, metrics));
            nseData.setSector(metrics.sector());
            nseData.setMarketCapType(metrics.marketCapType());
            nseData.setPe(metrics.pe());
            nseData.setBeta(metrics.beta());
            nseData.setWeek52High(metrics.week52High());
            nseData.setWeek52Low(metrics.week52Low());
            nseData.setSectorPe(metrics.sectorPe());
            nseData.setIssuedSize(metrics.issuedSize());
            nseData.setDma200(metrics.dma200());
            nseDataRepository.save(nseData);
            logger.info("Stored NSE data for symbol: " + upperSymbol + " | PE: " + metrics.pe() +
                    ", Beta: " + metrics.beta() + ", Week52High: " + metrics.week52High());
        }
    }

    /**
     * Clear all NSE data from the database (useful for testing or refresh).
     */
    public void clearCache() {
        nseDataRepository.deleteAll();
        logger.info("Cleared all NSE data from database");
    }

    /**
     * Get all stored NSE metrics.
     */
    public ConcurrentHashMap<String, StockMetrics> getAllMetrics() {
        ConcurrentHashMap<String, StockMetrics> result = new ConcurrentHashMap<>();
        nseDataRepository.findAll()
                .forEach(nseData -> result.put(nseData.getSymbol(), nseData.toStockMetrics()));
        return result;
    }
}
