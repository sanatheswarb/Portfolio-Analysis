package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.StockMetrics;
import com.cursor_springa_ai.playground.integration.market.NseApiClient;
import com.cursor_springa_ai.playground.model.entity.Instrument;
import com.cursor_springa_ai.playground.model.entity.StockFundamentals;
import com.cursor_springa_ai.playground.repository.StockFundamentalsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
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
    void refreshAndGetPreviousClose_storesEtfSectorFromMetrics() throws Exception {
        StockFundamentalsRepository repository = mock(StockFundamentalsRepository.class);
        NseApiClient nseApiClient = mock(NseApiClient.class);
        StockFundamentalsService service = new StockFundamentalsService(repository, nseApiClient);
        Instrument instrument = new Instrument(1L, "NIFTYBEES", "NSE", "INF204KB14I2");
        setField(instrument, "id", 10L);

        StockMetrics metrics = new StockMetrics(
                "NIFTYBEES", "ETF", "N/A",
                null, null, null, null, null,
                2235366385L, null, BigDecimal.valueOf(255.26),
                BigDecimal.valueOf(258.89)
        );

        when(repository.findByInstrumentId(10L)).thenReturn(Optional.empty());
        when(nseApiClient.fetchMetricsForSymbol("NIFTYBEES")).thenReturn(metrics);
        when(repository.save(any(StockFundamentals.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BigDecimal previousClose = service.refreshAndGetPreviousClose(instrument);
        ArgumentCaptor<StockFundamentals> savedFundamentals = ArgumentCaptor.forClass(StockFundamentals.class);

        verify(repository).save(savedFundamentals.capture());
        assertNotNull(previousClose);
        assertEquals(BigDecimal.valueOf(258.89), previousClose);
        assertEquals("ETF", savedFundamentals.getValue().getSector());
        assertEquals(10L, savedFundamentals.getValue().getInstrumentId());
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
