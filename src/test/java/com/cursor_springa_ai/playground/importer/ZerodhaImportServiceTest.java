package com.cursor_springa_ai.playground.importer;

import com.cursor_springa_ai.playground.dto.ZerodhaImportResponse;
import com.cursor_springa_ai.playground.integration.zerodha.ZerodhaHoldingsClient;
import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaHoldingItem;
import com.cursor_springa_ai.playground.model.Instrument;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.model.UserHolding;
import com.cursor_springa_ai.playground.service.InstrumentEnrichmentService;
import com.cursor_springa_ai.playground.service.PortfolioStatsBatchService;
import com.cursor_springa_ai.playground.service.StockFundamentalsService;
import com.cursor_springa_ai.playground.service.UserHoldingSyncService;
import com.cursor_springa_ai.playground.service.ZerodhaAuthService;
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
        HoldingPreparationService holdingPreparationService = new HoldingPreparationService(
            enrichmentService,
            fundamentalsService,
            new HoldingValueCalculator()
        );
        HoldingMergeService holdingMergeService = new HoldingMergeService();
        ZerodhaImportService service = new ZerodhaImportService(
                holdingsClient,
                authService,
            userHoldingSyncService,
            portfolioStatsBatchService,
            holdingPreparationService,
            holdingMergeService
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
        HoldingPreparationService holdingPreparationService = new HoldingPreparationService(
            enrichmentService,
            fundamentalsService,
            new HoldingValueCalculator()
        );
        HoldingMergeService holdingMergeService = new HoldingMergeService();
        ZerodhaImportService service = new ZerodhaImportService(
                holdingsClient,
                authService,
            userHoldingSyncService,
            portfolioStatsBatchService,
            holdingPreparationService,
            holdingMergeService
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

    @Test
    void importHoldings_mergesPreparedHoldingsThatResolveToSameInstrument() throws Exception {
        ZerodhaHoldingsClient holdingsClient = mock(ZerodhaHoldingsClient.class);
        ZerodhaAuthService authService = mock(ZerodhaAuthService.class);
        InstrumentEnrichmentService enrichmentService = mock(InstrumentEnrichmentService.class);
        StockFundamentalsService fundamentalsService = mock(StockFundamentalsService.class);
        UserHoldingSyncService userHoldingSyncService = mock(UserHoldingSyncService.class);
        PortfolioStatsBatchService portfolioStatsBatchService = mock(PortfolioStatsBatchService.class);
        HoldingPreparationService holdingPreparationService = new HoldingPreparationService(
            enrichmentService,
            fundamentalsService,
            new HoldingValueCalculator()
        );
        HoldingMergeService holdingMergeService = new HoldingMergeService();
        ZerodhaImportService service = new ZerodhaImportService(
                holdingsClient,
                authService,
                userHoldingSyncService,
            portfolioStatsBatchService,
            holdingPreparationService,
            holdingMergeService
        );

        User user = new User("ZERODHA", "portfolio-1");
        setField(user, "id", 1L);
        Instrument instrument = new Instrument(123L, "INFY", "NSE", "INE009A01021");
        setField(instrument, "id", 10L);

        ZerodhaHoldingItem first = holdingItem(
                123L,
                "INFY",
                BigDecimal.TEN,
                BigDecimal.valueOf(1500),
                BigDecimal.valueOf(1600)
        );
        ZerodhaHoldingItem second = holdingItem(
                456L,
                "INFY",
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(1500),
                BigDecimal.valueOf(1600)
        );

        when(authService.getCurrentUser()).thenReturn(user);
        when(holdingsClient.fetchHoldings()).thenReturn(List.of(first, second));
        when(enrichmentService.upsertAndEnrich(first)).thenReturn(instrument);
        when(enrichmentService.upsertAndEnrich(second)).thenReturn(instrument);
        when(fundamentalsService.upsertIfStale(instrument)).thenReturn(BigDecimal.valueOf(1550));

        ZerodhaImportResponse response = service.importHoldings();

        verify(userHoldingSyncService).replaceHoldings(eq(1L), argThat(list -> {
            if (!(list instanceof List<?> holdings) || holdings.size() != 1) {
                return false;
            }
            Object value = holdings.getFirst();
            if (!(value instanceof UserHolding holding)) {
                return false;
            }
            return holding.getInstrument().getId().equals(10L)
                    && holding.getQuantity() == 15
                    && holding.getInvestedValue().compareTo(BigDecimal.valueOf(22500)) == 0
                    && holding.getCurrentValue().compareTo(BigDecimal.valueOf(24000)) == 0;
        }));
        verify(portfolioStatsBatchService).calculateForUserAsync(1L);
        assertEquals(1, response.importedHoldings());
        assertEquals(List.of("INFY"), response.symbols());
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
