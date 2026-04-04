package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.integration.market.NseApiClient;
import com.cursor_springa_ai.playground.integration.market.dto.NseQuoteResponse;
import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaHoldingItem;
import com.cursor_springa_ai.playground.model.Instrument;
import com.cursor_springa_ai.playground.repository.InstrumentRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstrumentEnrichmentServiceTest {

    @Test
    void upsertAndEnrich_marksInstrumentSectorAsEtf() {
        InstrumentRepository repository = mock(InstrumentRepository.class);
        NseApiClient nseApiClient = mock(NseApiClient.class);
        InstrumentEnrichmentService service = new InstrumentEnrichmentService(repository, nseApiClient);
        ZerodhaHoldingItem item = holdingItem(1L, "NIFTYBEES");
        Instrument instrument = new Instrument(1L, "NIFTYBEES", "NSE", "INF204KB14I2");
        NseQuoteResponse quote = new NseQuoteResponse(
                new NseQuoteResponse.Info("NIFTYBEES", "Nippon India ETF Nifty 50 BeES", true),
                null,
                new NseQuoteResponse.PriceInfo(255.26, null, null, null, null),
                new NseQuoteResponse.IndustryInfo("Financial Services", "Capital Markets"),
                null
        );

        when(repository.findByInstrumentToken(1L)).thenReturn(Optional.of(instrument));
        when(nseApiClient.fetchQuote("NIFTYBEES")).thenReturn(Optional.of(quote));
        when(nseApiClient.resolveSector(quote)).thenReturn("ETF");
        when(repository.save(instrument)).thenReturn(instrument);

        Instrument enriched = service.upsertAndEnrich(item);

        assertNotNull(enriched.getLastEnriched());
        assertEquals("ETF", enriched.getSector());
        assertEquals("Capital Markets", enriched.getIndustry());
    }

    @Test
    void upsertAndEnrich_usesQuoteSectorForNonEtfInstrument() {
        InstrumentRepository repository = mock(InstrumentRepository.class);
        NseApiClient nseApiClient = mock(NseApiClient.class);
        InstrumentEnrichmentService service = new InstrumentEnrichmentService(repository, nseApiClient);
        ZerodhaHoldingItem item = holdingItem(2L, "INFY");
        Instrument instrument = new Instrument(2L, "INFY", "NSE", "INE009A01021");
        NseQuoteResponse quote = new NseQuoteResponse(
                new NseQuoteResponse.Info("INFY", "Infosys Limited", false),
                null,
                new NseQuoteResponse.PriceInfo(1500.0, null, null, null, null),
                new NseQuoteResponse.IndustryInfo("Information Technology", "Computers - Software"),
                null
        );

        when(repository.findByInstrumentToken(2L)).thenReturn(Optional.of(instrument));
        when(nseApiClient.fetchQuote("INFY")).thenReturn(Optional.of(quote));
        when(nseApiClient.resolveSector(quote)).thenReturn("Information Technology");
        when(repository.save(instrument)).thenReturn(instrument);

        Instrument enriched = service.upsertAndEnrich(item);

        assertNotNull(enriched.getLastEnriched());
        assertEquals("Information Technology", enriched.getSector());
        assertEquals("Computers - Software", enriched.getIndustry());
    }

    @Test
    void upsertAndEnrich_reusesExistingInstrumentWhenTokenChangesButIsinMatches() {
        InstrumentRepository repository = mock(InstrumentRepository.class);
        NseApiClient nseApiClient = mock(NseApiClient.class);
        InstrumentEnrichmentService service = new InstrumentEnrichmentService(repository, nseApiClient);
        ZerodhaHoldingItem item = holdingItem(200L, "INFY");
        item.setIsin("INE009A01021");

        Instrument existingInstrument = new Instrument(2L, "INFY", "NSE", "INE009A01021");
        existingInstrument.setLastEnriched(LocalDateTime.now());

        when(repository.findByInstrumentToken(200L)).thenReturn(Optional.empty());
        when(repository.findByIsinIgnoreCase("INE009A01021")).thenReturn(Optional.of(existingInstrument));

        Instrument resolved = service.upsertAndEnrich(item);

        assertEquals(2L, resolved.getInstrumentToken());
        verify(repository, never()).save(existingInstrument);
    }

    private ZerodhaHoldingItem holdingItem(Long token, String symbol) {
        ZerodhaHoldingItem item = new ZerodhaHoldingItem();
        item.setInstrumentToken(token);
        item.setTradingSymbol(symbol);
        item.setExchange("NSE");
        return item;
    }
}