package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.model.Instrument;
import com.cursor_springa_ai.playground.model.RiskFlag;
import com.cursor_springa_ai.playground.model.StockFundamentals;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.model.UserHolding;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class HoldingAnalyticsServiceTest {

    private final HoldingAnalyticsService service = new HoldingAnalyticsService();

    @Test
    void buildEnrichedHolding_usesCanonicalHoldingAnalyticsForFlagsAndDerivedMetrics() throws Exception {
        UserHolding holding = holding(
                "ABC",
                "Technology",
                "SMALL",
                BigDecimal.valueOf(30),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(200),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(140),
                BigDecimal.valueOf(25),
                BigDecimal.valueOf(45),
                BigDecimal.valueOf(-2.75)
        );

        EnrichedHoldingData enriched = service.buildEnrichedHolding(holding);

        assertEquals(new BigDecimal("-30.00"), enriched.distanceFromHigh());
        assertEquals("OVERVALUED", enriched.valuationFlag());
        assertEquals(new BigDecimal("70.00"), enriched.momentumScore());
        assertEquals(new BigDecimal("6"), enriched.riskScore());
        assertEquals(new BigDecimal("70.00"), service.computeMomentumScore(holding));
        assertEquals(new BigDecimal("2.75"), service.calculateVolatility(holding));
        assertEquals(new BigDecimal("6"), service.computeRiskScore(service.calculateVolatility(holding)));
        assertEquals("OVERVALUED", service.computeValuationFlag(enriched.pe(), enriched.sectorPe()));
        assertIterableEquals(
                List.of(
                        RiskFlag.HIGH_CONCENTRATION.name(),
                        RiskFlag.HIGH_VALUATION.name(),
                        RiskFlag.DEEP_CORRECTION.name(),
                        RiskFlag.SMALL_CAP_RISK.name(),
                        RiskFlag.PROFIT_BOOKING_ZONE.name()
                ),
                enriched.riskFlags()
        );
    }

    @Test
    void computeValuationFlag_respectsTwentyPercentBands() {
        assertEquals("OVERVALUED", service.computeValuationFlag(BigDecimal.valueOf(25), BigDecimal.valueOf(20)));
        assertEquals("UNDERVALUED", service.computeValuationFlag(BigDecimal.valueOf(15), BigDecimal.valueOf(20)));
        assertEquals("FAIRLY_VALUED", service.computeValuationFlag(BigDecimal.valueOf(24), BigDecimal.valueOf(20)));
    }

    private UserHolding holding(String symbol,
                                String sector,
                                String marketCapCategory,
                                BigDecimal pe,
                                BigDecimal sectorPe,
                                BigDecimal week52High,
                                BigDecimal week52Low,
                                BigDecimal lastPrice,
                                BigDecimal weightPercent,
                                BigDecimal pnlPercent,
                                BigDecimal dayChangePercent) throws Exception {
        User user = new User("ZERODHA", "user-1");
        setField(user, "id", 1L);

        Instrument instrument = new Instrument(101L, symbol, "NSE", null);
        instrument.setSector(sector);
        instrument.setMarketCapCategory(marketCapCategory);

        StockFundamentals fundamentals = new StockFundamentals(101L);
        fundamentals.setSector(sector);
        fundamentals.setPe(pe);
        fundamentals.setSectorPe(sectorPe);
        fundamentals.setWeek52High(week52High);
        fundamentals.setWeek52Low(week52Low);
        instrument.setStockFundamentals(fundamentals);

        UserHolding holding = new UserHolding(
                user,
                instrument,
                10,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(138),
                lastPrice,
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(1400),
                BigDecimal.valueOf(400),
                pnlPercent,
                BigDecimal.valueOf(-28),
                dayChangePercent
        );
        holding.setSymbol(symbol);
        holding.setWeightPercent(weightPercent);
        return holding;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
