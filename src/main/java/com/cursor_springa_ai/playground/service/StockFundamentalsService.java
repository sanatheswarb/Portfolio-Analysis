package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.integration.market.NseApiClient;
import com.cursor_springa_ai.playground.integration.market.dto.NseQuoteResponse;
import com.cursor_springa_ai.playground.model.Instrument;
import com.cursor_springa_ai.playground.model.StockFundamentals;
import com.cursor_springa_ai.playground.repository.StockFundamentalsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private static final int REFRESH_THRESHOLD_HOURS = 24;

    private final StockFundamentalsRepository fundamentalsRepository;
    private final NseApiClient nseApiClient;

    public StockFundamentalsService(StockFundamentalsRepository fundamentalsRepository,
                                    NseApiClient nseApiClient) {
        this.fundamentalsRepository = fundamentalsRepository;
        this.nseApiClient = nseApiClient;
    }

    /**
     * Upsert fundamentals for the given instrument.
     * Skips the NSE API call when the row was refreshed within the last 24 hours.
     */
    @Transactional
    public void upsertIfStale(Instrument instrument) {
        if (instrument == null) {
            return;
        }
        Long token = instrument.getInstrumentToken();
        String symbol = instrument.getSymbol();

        Optional<StockFundamentals> existing = fundamentalsRepository.findById(token);

        if (existing.isPresent()) {
            StockFundamentals row = existing.get();
            if (row.getLastUpdated() != null
                    && row.getLastUpdated().isAfter(LocalDateTime.now().minusHours(REFRESH_THRESHOLD_HOURS))) {
                return; // still fresh — skip
            }
            refreshFromNse(row, symbol);
        } else {
            StockFundamentals row = new StockFundamentals(instrument);
            refreshFromNse(row, symbol);
        }
    }

    // ------------------------------------------------------------------
    // private helpers
    // ------------------------------------------------------------------

    private void refreshFromNse(StockFundamentals row, String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return;
        }
        Optional<NseQuoteResponse> quoteOpt = nseApiClient.fetchQuote(symbol);
        if (quoteOpt.isEmpty()) {
            return;
        }
        NseQuoteResponse quote = quoteOpt.get();

        // PE ratio
        if (quote.metadata() != null && quote.metadata().pdSymbolPe() != null) {
            row.setPe(BigDecimal.valueOf(quote.metadata().pdSymbolPe()));
        }

        // Macro sector
        if (quote.industryInfo() != null && quote.industryInfo().sector() != null) {
            row.setSector(quote.industryInfo().sector());
        }

        // Market cap = issuedSize × lastPrice (in INR)
        if (quote.securityInfo() != null && quote.securityInfo().issuedSize() != null
                && quote.priceInfo() != null && quote.priceInfo().lastPrice() != null) {
            long marketCap = Math.round(
                    quote.securityInfo().issuedSize() * quote.priceInfo().lastPrice());
            row.setMarketCap(marketCap);
        }

        row.setLastUpdated(LocalDateTime.now());
        fundamentalsRepository.save(row);
        logger.info("StockFundamentals updated: symbol=" + symbol
                + " pe=" + row.getPe()
                + " marketCap=" + row.getMarketCap()
                + " sector=" + row.getSector());
    }
}
