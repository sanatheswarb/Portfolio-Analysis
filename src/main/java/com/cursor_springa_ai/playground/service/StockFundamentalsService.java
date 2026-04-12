package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.integration.market.NseApiClient;
import com.cursor_springa_ai.playground.dto.StockMetrics;
import com.cursor_springa_ai.playground.model.entity.Instrument;
import com.cursor_springa_ai.playground.model.entity.StockFundamentals;
import com.cursor_springa_ai.playground.repository.StockFundamentalsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Maintains the {@code stock_fundamentals} table.
 *
 * <p>On each holdings import, called once per instrument. Skips refresh if the row was
 * updated within the last 24 hours to avoid hammering the NSE API.
 *
 * <p>Fields populated from the NSE quote API: pe, market_cap, sector.
 * Fields requiring balance-sheet / income-statement data (pb, roe, debt_to_equity)
 * are left null until an additional enrichment source is integrated.
 */
@Service
public class StockFundamentalsService {

    private static final Logger logger = Logger.getLogger(StockFundamentalsService.class.getName());

    private final StockFundamentalsRepository fundamentalsRepository;
    private final NseApiClient nseApiClient;

    public StockFundamentalsService(StockFundamentalsRepository fundamentalsRepository,
                                    NseApiClient nseApiClient) {
        this.fundamentalsRepository = fundamentalsRepository;
        this.nseApiClient = nseApiClient;
    }

    /**
     * Refresh fundamentals for the given instrument (if stale) and return the current NSE previous close.
     * Fundamentals are persisted only when the existing record is missing or outdated (older than today).
     * Previous close is always fetched on-demand from the NSE quote API.
     */
    @Transactional
    public BigDecimal refreshAndGetPreviousClose(Instrument instrument) {
        if (instrument == null) {
            return null;
        }
        Long instrumentId = instrument.getId();
        if (instrumentId == null) {
            logger.warning("Skipping fundamentals refresh: instrument id is null");
            return null;
        }
        String symbol = instrument.getSymbol();

        Optional<StockFundamentals> existingFundamentals = fundamentalsRepository.findByInstrumentId(instrumentId);

        StockFundamentals fundamentals;
        if (existingFundamentals.isPresent()) {
            fundamentals = existingFundamentals.get();
            fundamentals.setSymbol(symbol);
            if (fundamentals.getLastUpdated() != null
                    && fundamentals.getLastUpdated().toLocalDate().isEqual(LocalDate.now())) {
                return fetchPreviousClose(symbol);
            }
        } else {
            fundamentals = new StockFundamentals(instrument);
            fundamentals.setSymbol(symbol);
        }
        return updateFundamentalsFromNse(fundamentals, symbol);
    }

    // ------------------------------------------------------------------
    // private helpers
    // ------------------------------------------------------------------

    private BigDecimal updateFundamentalsFromNse(StockFundamentals fundamentals, String symbol) {
        if (fundamentals.getInstrumentId() == null) {
            logger.warning("Skipping fundamentals save: null instrument id for symbol=" + symbol);
            return null;
        }

        if (symbol == null || symbol.isBlank()) {
            return null;
        }

        StockMetrics metrics = nseApiClient.fetchMetricsForSymbol(symbol);
        if (metrics == null) {
            return null;
        }

        if (metrics.pe() != null) {
            fundamentals.setPe(metrics.pe());
        }

        if (metrics.sector() != null && !metrics.sector().isBlank() && !"N/A".equals(metrics.sector())) {
            fundamentals.setSector(metrics.sector());
        }

        if (metrics.week52High() != null) {
            fundamentals.setWeek52High(metrics.week52High());
        }
        if (metrics.week52Low() != null) {
            fundamentals.setWeek52Low(metrics.week52Low());
        }

        if (metrics.sectorPe() != null) {
            fundamentals.setSectorPe(metrics.sectorPe());
        }

        // Market cap = issuedSize × lastPrice (in INR)
        if (metrics.issuedSize() != null && metrics.lastPrice() != null
                && metrics.lastPrice().compareTo(BigDecimal.ZERO) > 0) {
            fundamentals.setMarketCap(metrics.issuedSize() * metrics.lastPrice().longValue());
        }

        fundamentals.setLastUpdated(LocalDateTime.now());
        fundamentalsRepository.save(fundamentals);
        logger.info("StockFundamentals updated: symbol=" + symbol
                + " pe=" + fundamentals.getPe()
                + " sectorPe=" + fundamentals.getSectorPe()
                + " week52High=" + fundamentals.getWeek52High()
                + " week52Low=" + fundamentals.getWeek52Low()
                + " marketCap=" + fundamentals.getMarketCap()
                + " sector=" + fundamentals.getSector());
        return metrics.previousClose();
    }

    private BigDecimal fetchPreviousClose(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        StockMetrics metrics = nseApiClient.fetchMetricsForSymbol(symbol);
        return metrics != null ? metrics.previousClose() : null;
    }
}
