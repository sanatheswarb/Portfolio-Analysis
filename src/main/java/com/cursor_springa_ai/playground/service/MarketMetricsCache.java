package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.StockMetrics;
import com.cursor_springa_ai.playground.integration.market.NseApiClient;
import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaHoldingItem;
import com.cursor_springa_ai.playground.model.NseData;
import com.cursor_springa_ai.playground.repository.NseDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
     * Skips symbols whose data was already fetched today (updatedAt >= start of today).
     * Re-fetches symbols whose data is stale (updatedAt before today) or not yet stored.
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

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

        // Query once: all stored records for the requested symbols
        List<NseData> storedRecords = nseDataRepository.findBySymbolIn(allSymbols);

        // Symbols already stored with fresh data (updated today) — skip these
        Set<String> freshSymbols = storedRecords.stream()
                .filter(d -> d.getUpdatedAt() != null && !d.getUpdatedAt().isBefore(startOfToday))
                .map(NseData::getSymbol)
                .collect(Collectors.toSet());

        // Symbols that are stale (exist in DB but updated before today) — update their records
        Map<String, NseData> staleRecords = storedRecords.stream()
                .filter(d -> d.getUpdatedAt() == null || d.getUpdatedAt().isBefore(startOfToday))
                .collect(Collectors.toMap(NseData::getSymbol, d -> d));

        // Symbols with no DB record at all — need to insert
        Set<String> allStoredSymbols = storedRecords.stream()
                .map(NseData::getSymbol)
                .collect(Collectors.toSet());
        Set<String> missingSymbols = allSymbols.stream()
                .filter(s -> !allStoredSymbols.contains(s))
                .collect(Collectors.toSet());

        // Holdings that need fetching: stale + missing
        List<ZerodhaHoldingItem> holdingsToFetch = holdings.stream()
                .filter(h -> h.getTradingSymbol() != null
                        && !freshSymbols.contains(h.getTradingSymbol().toUpperCase()))
                .collect(Collectors.toList());

        if (holdingsToFetch.isEmpty()) {
            logger.info("All NSE data is fresh for today. Skipping API fetch. Total symbols: " + freshSymbols.size());
            return;
        }

        logger.info("Fetching NSE data for " + holdingsToFetch.size() + " symbol(s). "
                + "Stale: " + staleRecords.size() + ", Missing: " + missingSymbols.size());

        Map<String, StockMetrics> fetchedMetrics = nseApiClient.fetchMetricsForHoldings(holdingsToFetch);

        for (Map.Entry<String, StockMetrics> entry : fetchedMetrics.entrySet()) {
            String symbol = entry.getKey().toUpperCase();
            StockMetrics metrics = entry.getValue();

            NseData nseData = staleRecords.getOrDefault(symbol,
                    NseData.fromStockMetrics(symbol, metrics)); // new record if not stale

            applyMetrics(nseData, metrics);

            nseDataRepository.save(nseData);
            logger.info("Saved NSE data for symbol: " + symbol + " | PE: " + metrics.pe()
                    + ", Sector: " + metrics.sector());
        }
        logger.info("Batch NSE data update completed. Total stored: " + nseDataRepository.count());
    }

    /**
     * Get metrics for a symbol.
     * Returns the stored value if it was updated today; otherwise re-fetches from NSE API.
     */
    public StockMetrics getMetrics(String symbol) {
        String upperSymbol = symbol.toUpperCase();
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

        return nseDataRepository.findById(upperSymbol)
                .filter(d -> d.getUpdatedAt() != null && !d.getUpdatedAt().isBefore(startOfToday))
                .map(NseData::toStockMetrics)
                .orElseGet(() -> {
                    StockMetrics fetched = nseApiClient.fetchMetricsForSymbol(upperSymbol);
                    if (fetched != null) {
                        NseData nseData = nseDataRepository.findById(upperSymbol)
                                .orElseGet(NseData::new);
                        applyMetrics(nseData, fetched);
                        if (nseData.getSymbol() == null) {
                            nseData.setSymbol(upperSymbol);
                        }
                        nseDataRepository.save(nseData);
                        logger.info("Fetched and stored NSE data for symbol: " + upperSymbol);
                    }
                    return fetched;
                });
    }

    /**
     * Store metrics in the database (updates existing record if present, preserving created_at).
     */
    public void cacheMetrics(String symbol, StockMetrics metrics) {
        if (metrics != null) {
            String upperSymbol = symbol.toUpperCase();
            NseData nseData = nseDataRepository.findById(upperSymbol)
                    .orElseGet(NseData::new);
            if (nseData.getSymbol() == null) {
                nseData.setSymbol(upperSymbol);
            }
            applyMetrics(nseData, metrics);
            nseDataRepository.save(nseData);
            logger.info("Stored NSE data for symbol: " + upperSymbol + " | PE: " + metrics.pe()
                    + ", Beta: " + metrics.beta() + ", Week52High: " + metrics.week52High());
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

    private void applyMetrics(NseData nseData, StockMetrics metrics) {
        nseData.setSector(metrics.sector());
        nseData.setMarketCapType(metrics.marketCapType());
        nseData.setPe(metrics.pe());
        nseData.setBeta(metrics.beta());
        nseData.setWeek52High(metrics.week52High());
        nseData.setWeek52Low(metrics.week52Low());
        nseData.setSectorPe(metrics.sectorPe());
        nseData.setIssuedSize(metrics.issuedSize());
        nseData.setDma200(metrics.dma200());
    }
}
