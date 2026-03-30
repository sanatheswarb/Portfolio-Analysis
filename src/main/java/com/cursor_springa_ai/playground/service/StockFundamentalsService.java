package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.integration.market.NseApiClient;
import com.cursor_springa_ai.playground.dto.StockMetrics;
import com.cursor_springa_ai.playground.model.Instrument;
import com.cursor_springa_ai.playground.model.StockFundamentals;
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
     * Upsert fundamentals for the given instrument and return the current NSE previous close.
     * Fundamentals persistence remains stale-aware, but previous close is fetched on-demand and not stored.
     */
    @Transactional
    public BigDecimal upsertIfStale(Instrument instrument) {
        if (instrument == null) {
            return null;
        }
        Long token = instrument.getInstrumentToken();
        if (token == null) {
            logger.warning("Skipping fundamentals upsert: instrument token is null");
            return null;
        }
        String symbol = instrument.getSymbol();

        Optional<StockFundamentals> existing = fundamentalsRepository.findById(token);

        if (existing.isPresent()) {
            StockFundamentals row = existing.get();
            row.setSymbol(symbol);
            if (row.getLastUpdated() != null
                    && row.getLastUpdated().toLocalDate().isEqual(LocalDate.now())) {
                return fetchPreviousClose(symbol);
            }
            return refreshFromNse(row, symbol);
        } else {
            StockFundamentals row = new StockFundamentals(token);
            row.setSymbol(symbol);
            return refreshFromNse(row, symbol);
        }
    }

    // ------------------------------------------------------------------
    // private helpers
    // ------------------------------------------------------------------

    private BigDecimal refreshFromNse(StockFundamentals row, String symbol) {
        if (row.getInstrumentToken() == null) {
            logger.warning("Skipping fundamentals save: null instrument token for symbol=" + symbol);
            return null;
        }

        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        Optional<com.cursor_springa_ai.playground.integration.market.dto.NseQuoteResponse> quoteOpt = nseApiClient.fetchQuote(symbol);
        if (quoteOpt.isEmpty()) {
            return null;
        }
        com.cursor_springa_ai.playground.integration.market.dto.NseQuoteResponse quote = quoteOpt.get();
        BigDecimal previousClose = extractPreviousClose(quote);

        StockMetrics metrics = nseApiClient.fetchMetricsForSymbol(symbol);

        // PE ratio
        if (metrics != null && metrics.pe() != null) {
            row.setPe(metrics.pe());
        }

        // Sector name returned by metrics endpoint
        if (metrics != null && metrics.sector() != null && !metrics.sector().isBlank()) {
            row.setSector(metrics.sector());
        } else if (quote.industryInfo() != null && quote.industryInfo().sector() != null
                && !quote.industryInfo().sector().isBlank()) {
            row.setSector(quote.industryInfo().sector());
        }

        // 52-week high / low
        if (metrics != null && metrics.week52High() != null) {
            row.setWeek52High(metrics.week52High());
        }
        if (metrics != null && metrics.week52Low() != null) {
            row.setWeek52Low(metrics.week52Low());
        }

        // Sector PE
        if (metrics != null && metrics.sectorPe() != null) {
            row.setSectorPe(metrics.sectorPe());
        }

        // Market cap = issuedSize × lastPrice (in INR)
        if (metrics != null && metrics.issuedSize() != null && metrics.lastPrice() != null
                && metrics.lastPrice().compareTo(BigDecimal.ZERO) > 0) {
            row.setMarketCap(metrics.issuedSize() * metrics.lastPrice().longValue());
        }

        row.setLastUpdated(LocalDateTime.now());
        fundamentalsRepository.save(row);
        logger.info("StockFundamentals updated: symbol=" + symbol
                + " pe=" + row.getPe()
                + " sectorPe=" + row.getSectorPe()
                + " week52High=" + row.getWeek52High()
                + " week52Low=" + row.getWeek52Low()
                + " marketCap=" + row.getMarketCap()
                + " sector=" + row.getSector());
        return previousClose;
    }

    private BigDecimal extractPreviousClose(com.cursor_springa_ai.playground.integration.market.dto.NseQuoteResponse quote) {
        if (quote == null || quote.priceInfo() == null) {
            return null;
        }
        if (quote.priceInfo().previousClose() != null) {
            return BigDecimal.valueOf(quote.priceInfo().previousClose());
        }
        if (quote.priceInfo().close() != null) {
            return BigDecimal.valueOf(quote.priceInfo().close());
        }
        return null;
    }

    private BigDecimal fetchPreviousClose(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        Optional<com.cursor_springa_ai.playground.integration.market.dto.NseQuoteResponse> quoteOpt = nseApiClient.fetchQuote(symbol);
        return quoteOpt.map(this::extractPreviousClose).orElse(null);
    }
}
