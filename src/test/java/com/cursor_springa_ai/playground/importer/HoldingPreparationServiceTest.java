package com.cursor_springa_ai.playground.importer;

import com.cursor_springa_ai.playground.dto.zerodha.ZerodhaHoldingItem;
import com.cursor_springa_ai.playground.service.InstrumentEnrichmentService;
import com.cursor_springa_ai.playground.service.StockFundamentalsService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class HoldingPreparationServiceTest {

    private final HoldingPreparationService service = new HoldingPreparationService(
            mock(InstrumentEnrichmentService.class),
            mock(StockFundamentalsService.class),
            new HoldingValueCalculator(),
            "^[A-Z0-9]+$"
    );

    @Test
    void isImportableHolding_acceptsValidSymbolWithPositiveQuantity() {
        ZerodhaHoldingItem item = holdingItem("INFY", BigDecimal.TEN);
        assertTrue(service.isImportableHolding(item));
    }

    @Test
    void isImportableHolding_rejectsNullSymbol() {
        ZerodhaHoldingItem item = holdingItem(null, BigDecimal.TEN);
        assertFalse(service.isImportableHolding(item));
    }

    @Test
    void isImportableHolding_rejectsZeroQuantity() {
        ZerodhaHoldingItem item = holdingItem("INFY", BigDecimal.ZERO);
        assertFalse(service.isImportableHolding(item));
    }

    @Test
    void isImportableHolding_rejectsNullQuantity() {
        ZerodhaHoldingItem item = holdingItem("INFY", null);
        assertFalse(service.isImportableHolding(item));
    }

    @Test
    void isImportableHolding_rejectsSymbolWithSpecialCharacters() {
        ZerodhaHoldingItem item = holdingItem("M&M", BigDecimal.TEN);
        assertFalse(service.isImportableHolding(item));
    }

    @Test
    void isImportableHolding_acceptsSymbolAfterNormalization() {
        ZerodhaHoldingItem item = holdingItem(" infy ", BigDecimal.TEN);
        assertTrue(service.isImportableHolding(item));
    }

    @Test
    void constructor_rethrowsInvalidSymbolPatternWithConfigContext() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new HoldingPreparationService(
                        mock(InstrumentEnrichmentService.class),
                        mock(StockFundamentalsService.class),
                        new HoldingValueCalculator(),
                        "[A-Z"
                ));

        assertEquals("Invalid configuration for zerodha.import.symbol-pattern: [A-Z", exception.getMessage());
        assertEquals(PatternSyntaxException.class, exception.getCause().getClass());
    }

    private ZerodhaHoldingItem holdingItem(String symbol, BigDecimal quantity) {
        ZerodhaHoldingItem item = new ZerodhaHoldingItem();
        item.setInstrumentToken(123L);
        item.setTradingSymbol(symbol);
        item.setExchange("NSE");
        item.setQuantity(quantity);
        item.setAveragePrice(BigDecimal.valueOf(1500));
        item.setLastPrice(BigDecimal.valueOf(1600));
        item.setPnl(BigDecimal.ZERO);
        item.setDayChange(BigDecimal.ZERO);
        item.setDayChangePercentage(BigDecimal.ZERO);
        return item;
    }
}

