package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.ZerodhaImportResponse;
import com.cursor_springa_ai.playground.integration.zerodha.ZerodhaHoldingsClient;
import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaHoldingItem;
import com.cursor_springa_ai.playground.model.Instrument;
import com.cursor_springa_ai.playground.model.User;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ZerodhaImportServiceTest {

    @Test
    void importHoldings_replacesExistingUserHoldingsForCurrentUser() throws Exception {
        ZerodhaHoldingsClient holdingsClient = mock(ZerodhaHoldingsClient.class);
        ZerodhaAuthService authService = mock(ZerodhaAuthService.class);
        InstrumentEnrichmentService enrichmentService = mock(InstrumentEnrichmentService.class);
        StockFundamentalsService fundamentalsService = mock(StockFundamentalsService.class);
        UserHoldingSyncService userHoldingSyncService = mock(UserHoldingSyncService.class);
        PortfolioStatsBatchService portfolioStatsBatchService = mock(PortfolioStatsBatchService.class);
        ZerodhaImportService service = new ZerodhaImportService(
                holdingsClient,
                authService,
                enrichmentService,
                fundamentalsService,
            userHoldingSyncService,
            portfolioStatsBatchService
        );

        User user = new User("ZERODHA", "portfolio-1");
        setField(user, "id", 1L);
        Instrument instrument = new Instrument(123L, "INFY", "NSE", "INE009A01021");
        setField(instrument, "id", 10L);
        ZerodhaHoldingItem item = holdingItem(123L, "INFY", BigDecimal.TEN, BigDecimal.valueOf(1500), BigDecimal.valueOf(1600));

        when(authService.getCurrentUser()).thenReturn(user);
        when(holdingsClient.fetchHoldings()).thenReturn(List.of(item));
        when(enrichmentService.upsertAndEnrich(item)).thenReturn(instrument);
        when(fundamentalsService.upsertIfStale(instrument)).thenReturn(BigDecimal.valueOf(1550));

        ZerodhaImportResponse response = service.importHoldings();

        InOrder inOrder = inOrder(userHoldingSyncService, portfolioStatsBatchService);
        inOrder.verify(userHoldingSyncService).replaceHoldings(eq(1L), argThat(list -> list instanceof List));
        inOrder.verify(portfolioStatsBatchService).calculateForUserAsync(1L);
        assertEquals("portfolio-1", response.portfolioUserId());
        assertEquals(1, response.importedHoldings());
        assertEquals(List.of("INFY"), response.symbols());
    }

    @Test
    void importHoldings_abortsBeforeReplacingHoldingsWhenAnyHoldingFails() throws Exception {
        ZerodhaHoldingsClient holdingsClient = mock(ZerodhaHoldingsClient.class);
        ZerodhaAuthService authService = mock(ZerodhaAuthService.class);
        InstrumentEnrichmentService enrichmentService = mock(InstrumentEnrichmentService.class);
        StockFundamentalsService fundamentalsService = mock(StockFundamentalsService.class);
        UserHoldingSyncService userHoldingSyncService = mock(UserHoldingSyncService.class);
        PortfolioStatsBatchService portfolioStatsBatchService = mock(PortfolioStatsBatchService.class);
        ZerodhaImportService service = new ZerodhaImportService(
                holdingsClient,
                authService,
                enrichmentService,
                fundamentalsService,
            userHoldingSyncService,
            portfolioStatsBatchService
        );

        User user = new User("ZERODHA", "portfolio-1");
        setField(user, "id", 1L);
        ZerodhaHoldingItem item = holdingItem(123L, "INFY", BigDecimal.TEN, BigDecimal.valueOf(1500), BigDecimal.valueOf(1600));

        when(authService.getCurrentUser()).thenReturn(user);
        when(holdingsClient.fetchHoldings()).thenReturn(List.of(item));
        when(enrichmentService.upsertAndEnrich(item)).thenThrow(new IllegalStateException("boom"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, service::importHoldings);

        verify(userHoldingSyncService, never()).replaceHoldings(any(), any());
        verify(portfolioStatsBatchService, never()).calculateForUserAsync(any());
        assertEquals("Import aborted; failed holdings: INFY", exception.getMessage());
        assertEquals("boom", exception.getCause().getMessage());
    }

    private ZerodhaHoldingItem holdingItem(Long token,
                                           String symbol,
                                           BigDecimal quantity,
                                           BigDecimal averagePrice,
                                           BigDecimal lastPrice) {
        ZerodhaHoldingItem item = new ZerodhaHoldingItem();
        item.setInstrumentToken(token);
        item.setTradingSymbol(symbol);
        item.setExchange("NSE");
        item.setQuantity(quantity);
        item.setAveragePrice(averagePrice);
        item.setLastPrice(lastPrice);
        item.setPnl(BigDecimal.ZERO);
        item.setDayChange(BigDecimal.ZERO);
        item.setDayChangePercentage(BigDecimal.ZERO);
        return item;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
