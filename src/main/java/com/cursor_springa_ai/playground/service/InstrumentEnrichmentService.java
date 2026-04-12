package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.integration.market.NseApiClient;
import com.cursor_springa_ai.playground.integration.market.dto.NseQuoteResponse;
import com.cursor_springa_ai.playground.dto.zerodha.ZerodhaHoldingItem;
import com.cursor_springa_ai.playground.model.entity.Instrument;
import com.cursor_springa_ai.playground.repository.InstrumentRepository;
import com.cursor_springa_ai.playground.util.StringNormalizer;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * How many months before a previously-enriched instrument is re-fetched from the NSE quote
     * API. Defaults to 1 month. Configurable via {@code instrument.enrichment.refresh-months}.
     * <p>Increasing this value reduces NSE quote traffic during bulk imports at the cost of
     * potentially stale metadata (sector, industry, market-cap category). Set to {@code 0} to
     * always re-enrich.
     */
    private final int enrichmentRefreshMonths;

    public InstrumentEnrichmentService(
            InstrumentRepository instrumentRepository,
            NseApiClient nseApiClient,
            @Value("${instrument.enrichment.refresh-months:1}") int enrichmentRefreshMonths) {
        this.instrumentRepository = instrumentRepository;
        this.nseApiClient = nseApiClient;
        this.enrichmentRefreshMonths = enrichmentRefreshMonths;
    }

    /**
     * Ensure the instrument for the given holding item exists in the catalogue and is enriched.
     *
     * @param item a holding item from the Zerodha API
     * @return the persisted {@link Instrument}
     */
    @Transactional
    public Instrument resolveInstrument(ZerodhaHoldingItem item) {
        Long token = item.getInstrumentToken();

        // 1. Try by instrument token
        Optional<Instrument> existing = token != null
                ? instrumentRepository.findByInstrumentToken(token)
                : Optional.empty();

        // 2. Fallback: try by ISIN, then by symbol + exchange
        if (existing.isEmpty()) {
            existing = findExistingInstrument(item);
        }

        // 3. Nothing found — insert a minimal row (requires an instrument token for de-duplication)
        Instrument instrument = existing.orElseGet(
                () -> token != null ? insertMinimal(item) : null);

        if (instrument == null) {
            return null;
        }


        if (instrument.getLastEnriched() == null
                || instrument.getLastEnriched().isBefore(LocalDateTime.now().minusMonths(enrichmentRefreshMonths))) {
            enrichFromNse(instrument, StringNormalizer.normalize(item.getTradingSymbol()));
        }

        return instrument;
    }

    // ------------------------------------------------------------------
    // private helpers
    // ------------------------------------------------------------------

    private Optional<Instrument> findExistingInstrument(ZerodhaHoldingItem item) {
        String isin = StringNormalizer.normalize(item.getIsin());
        if (isin != null) {
            Optional<Instrument> existingByIsin = instrumentRepository.findByIsinIgnoreCase(isin);
            if (existingByIsin.isPresent()) {
                logTokenMismatch(item, existingByIsin.get(), "isin");
                return existingByIsin;
            }
        }

        String symbol = StringNormalizer.normalize(item.getTradingSymbol());
        String exchange = StringNormalizer.normalize(item.getExchange()) != null
                ? StringNormalizer.normalize(item.getExchange()) : "NSE";
        if (symbol == null) {
            return Optional.empty();
        }

        Optional<Instrument> existingBySymbol = instrumentRepository.findBySymbolAndExchangeIgnoreCase(symbol, exchange);
        existingBySymbol.ifPresent(instrument -> logTokenMismatch(item, instrument, "symbol+exchange"));
        return existingBySymbol;
    }

    private Instrument insertMinimal(ZerodhaHoldingItem item) {
        String normalizedExchange = StringNormalizer.normalize(item.getExchange());
        Instrument instrument = new Instrument(
                item.getInstrumentToken(),
                StringNormalizer.normalize(item.getTradingSymbol()),
                normalizedExchange != null ? normalizedExchange : "NSE",
                StringNormalizer.normalize(item.getIsin())
        );
        logger.info("Inserting new instrument: token=" + item.getInstrumentToken()
                + " symbol=" + item.getTradingSymbol());
        return instrumentRepository.save(instrument);
    }

    private void logTokenMismatch(ZerodhaHoldingItem item, Instrument instrument, String matchedBy) {
        if (item.getInstrumentToken() == null) {
            return;
        }
        if (item.getInstrumentToken().equals(instrument.getInstrumentToken())) {
            return;
        }
        logger.warning("Incoming instrument token " + item.getInstrumentToken()
                + " matched existing instrument token " + instrument.getInstrumentToken()
                + " via " + matchedBy
                + " for symbol=" + item.getTradingSymbol()
                + ". Reusing existing instrument row.");
    }


    private void enrichFromNse(Instrument instrument, String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return;
        }
        Optional<NseQuoteResponse> quoteOpt = nseApiClient.fetchQuote(symbol);
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
        double marketCapInCrores = (quote.securityInfo().issuedSize() * quote.priceInfo().lastPrice()) / 1e7;
        if (marketCapInCrores >= 20_000) {
            return "LARGE_CAP";
        }
        if (marketCapInCrores >= 5_000) {
            return "MID_CAP";
        }
        return "SMALL_CAP";
    }
}
