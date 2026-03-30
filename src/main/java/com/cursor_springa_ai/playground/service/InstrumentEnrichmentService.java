package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.integration.market.NseApiClient;
import com.cursor_springa_ai.playground.integration.market.dto.NseQuoteResponse;
import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaHoldingItem;
import com.cursor_springa_ai.playground.model.Instrument;
import com.cursor_springa_ai.playground.repository.InstrumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Manages the instruments catalogue table.
 *
 * <p>On each holdings import:
 * <ol>
 *   <li>Insert a new row with the minimum data available from the Zerodha holdings API if
 *       the instrument_token is not present yet.</li>
 *   <li>If the row has never been enriched ({@code last_enriched} is null), attempt to
 *       fetch richer metadata (company name, sector, industry, market-cap category)
 *       from the NSE quote API and persist it.</li>
 * </ol>
 */
@Service
public class InstrumentEnrichmentService {

    private static final Logger logger = Logger.getLogger(InstrumentEnrichmentService.class.getName());

    private final InstrumentRepository instrumentRepository;
    private final NseApiClient nseApiClient;

    public InstrumentEnrichmentService(InstrumentRepository instrumentRepository, NseApiClient nseApiClient) {
        this.instrumentRepository = instrumentRepository;
        this.nseApiClient = nseApiClient;
    }

    /**
     * Ensure the instrument for the given holding item exists in the catalogue and is enriched.
     *
     * @param item a holding item from the Zerodha API
     * @return the persisted {@link Instrument}
     */
    @Transactional
    public Instrument upsertAndEnrich(ZerodhaHoldingItem item) {
        Long token = item.getInstrumentToken();
        if (token == null) {
            return null;
        }

        Instrument instrument = instrumentRepository.findById(token)
                .orElseGet(() -> insertMinimal(item));

        if (instrument.getLastEnriched() == null) {
            tryEnrich(instrument, item.getTradingSymbol());
        }

        return instrument;
    }

    // ------------------------------------------------------------------
    // private helpers
    // ------------------------------------------------------------------

    private Instrument insertMinimal(ZerodhaHoldingItem item) {
        Instrument instrument = new Instrument(
                item.getInstrumentToken(),
                item.getTradingSymbol() != null ? item.getTradingSymbol().toUpperCase() : null,
                item.getExchange() != null ? item.getExchange() : "NSE",
                item.getIsin()
        );
        logger.info("Inserting new instrument: token=" + item.getInstrumentToken()
                + " symbol=" + item.getTradingSymbol());
        return instrumentRepository.save(instrument);
    }

    private void tryEnrich(Instrument instrument, String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return;
        }
        Optional<NseQuoteResponse> quoteOpt = nseApiClient.fetchQuote(symbol.toUpperCase());
        if (quoteOpt.isEmpty()) {
            return;
        }
        NseQuoteResponse quote = quoteOpt.get();

        if (quote.info() != null && quote.info().companyName() != null) {
            instrument.setCompanyName(quote.info().companyName());
        }
        String sector = nseApiClient.resolveSector(quote);
        if (!sector.isBlank() && !"N/A".equals(sector)) {
            instrument.setSector(sector);
        }
        if (quote.industryInfo() != null) {
            if (quote.industryInfo().industry() != null) {
                instrument.setIndustry(quote.industryInfo().industry());
            }
        }

        String marketCapCategory = deriveMarketCapCategory(quote);
        if (marketCapCategory != null) {
            instrument.setMarketCapCategory(marketCapCategory);
        }

        instrument.setLastEnriched(LocalDateTime.now());
        instrumentRepository.save(instrument);
        logger.info("Enriched instrument: symbol=" + symbol
                + " sector=" + instrument.getSector()
                + " industry=" + instrument.getIndustry()
                + " marketCap=" + instrument.getMarketCapCategory());
    }

    /**
     * Derive a broad market-cap category from issued size × last price (converted to INR crores).
     *
     * <ul>
     *   <li>LARGE_CAP  – market cap ≥ ₹20 000 Cr</li>
     *   <li>MID_CAP    – ₹5 000 Cr ≤ market cap {@literal <} ₹20 000 Cr</li>
     *   <li>SMALL_CAP  – market cap {@literal <} ₹5 000 Cr</li>
     * </ul>
     */
    private String deriveMarketCapCategory(NseQuoteResponse quote) {
        if (quote.securityInfo() == null || quote.securityInfo().issuedSize() == null) {
            return null;
        }
        if (quote.priceInfo() == null || quote.priceInfo().lastPrice() == null) {
            return null;
        }
        double marketCapCrore = (quote.securityInfo().issuedSize() * quote.priceInfo().lastPrice()) / 1e7;
        if (marketCapCrore >= 20_000) {
            return "LARGE_CAP";
        }
        if (marketCapCrore >= 5_000) {
            return "MID_CAP";
        }
        return "SMALL_CAP";
    }
}
