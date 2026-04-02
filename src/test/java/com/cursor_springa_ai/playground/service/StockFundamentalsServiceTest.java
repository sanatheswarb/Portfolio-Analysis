package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.integration.market.NseApiClient;
import com.cursor_springa_ai.playground.integration.market.dto.NseQuoteResponse;
import com.cursor_springa_ai.playground.model.Instrument;
import com.cursor_springa_ai.playground.model.StockFundamentals;
import com.cursor_springa_ai.playground.repository.StockFundamentalsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockFundamentalsServiceTest {

    @Test
    void upsertIfStale_storesEtfSectorFromQuoteWhenMetricsAreUnavailable() {
        StockFundamentalsRepository repository = mock(StockFundamentalsRepository.class);
        NseApiClient nseApiClient = mock(NseApiClient.class);
        StockFundamentalsService service = new StockFundamentalsService(repository, nseApiClient);
        Instrument instrument = new Instrument(1L, "NIFTYBEES", "NSE", "INF204KB14I2");
        NseQuoteResponse quote = new NseQuoteResponse(
                new NseQuoteResponse.Info("NIFTYBEES", "Nippon India ETF Nifty 50 BeES", true),
                null,
                new NseQuoteResponse.PriceInfo(255.26, 253.44, 258.89, -1.40, null),
                new NseQuoteResponse.IndustryInfo("Financial Services", "Capital Markets"),
                new NseQuoteResponse.SecurityInfo(2235366385L)
        );

        when(repository.findById(1L)).thenReturn(Optional.empty());
        when(nseApiClient.fetchQuote("NIFTYBEES")).thenReturn(Optional.of(quote));
        when(nseApiClient.fetchMetricsForSymbol("NIFTYBEES")).thenReturn(null);
        when(nseApiClient.resolveSector(quote)).thenReturn("ETF");
        when(repository.save(any(StockFundamentals.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BigDecimal previousClose = service.upsertIfStale(instrument);
        ArgumentCaptor<StockFundamentals> savedFundamentals = ArgumentCaptor.forClass(StockFundamentals.class);

        verify(repository).save(savedFundamentals.capture());
        assertNotNull(previousClose);
        assertEquals(BigDecimal.valueOf(258.89), previousClose);
        assertEquals("ETF", savedFundamentals.getValue().getSector());
    }
}