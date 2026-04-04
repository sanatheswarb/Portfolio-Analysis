package com.cursor_springa_ai.playground.analytics;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.Instrument;
import com.cursor_springa_ai.playground.model.PortfolioStats;
import com.cursor_springa_ai.playground.model.RiskFlag;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.model.UserHolding;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioAnalyticsServiceTest {

    private final HoldingAnalyticsService holdingAnalyticsService = new HoldingAnalyticsService();
    private final PortfolioAnalyticsService portfolioAnalyticsService = new PortfolioAnalyticsService();

    @Test
    void calculatePortfolioStats_andRiskFlags_useSinglePortfolioAnalyticsSource() throws Exception {
        User user = new User("ZERODHA", "portfolio-1");
        setField(user, "id", 42L);

        List<UserHolding> holdings = List.of(
                holding(user, 101L, "INFY", "Technology", BigDecimal.valueOf(100), BigDecimal.valueOf(120), BigDecimal.valueOf(5), BigDecimal.valueOf(50)),
                holding(user, 102L, "TCS", "Technology", BigDecimal.valueOf(100), BigDecimal.valueOf(90), BigDecimal.valueOf(-3), BigDecimal.valueOf(20)),
                holding(user, 103L, "HDFCBANK", "Banking", BigDecimal.valueOf(50), BigDecimal.valueOf(55), BigDecimal.ONE, BigDecimal.valueOf(15)),
                holding(user, 104L, "SUNPHARMA", "Pharma", BigDecimal.valueOf(50), BigDecimal.valueOf(60), BigDecimal.valueOf(2), BigDecimal.valueOf(15))
        );

        PortfolioStats stats = portfolioAnalyticsService.calculatePortfolioStats(user, holdings, LocalDateTime.now());
        PortfolioSummary summary = portfolioAnalyticsService.toPortfolioSummary(stats);
        List<EnrichedHoldingData> enrichedHoldings = holdingAnalyticsService.buildEnrichedHoldings(holdings);
        List<String> riskFlags = portfolioAnalyticsService.calculatePortfolioRiskFlags(stats, enrichedHoldings);

        assertEquals(new BigDecimal("300"), stats.getTotalInvested());
        assertEquals(new BigDecimal("325"), stats.getTotalValue());
        assertEquals(new BigDecimal("25"), stats.getTotalPnl());
        assertEquals(new BigDecimal("50"), stats.getLargestWeight());
        assertEquals(new BigDecimal("85"), stats.getTop3HoldingPercent());
        assertEquals(new BigDecimal("0.8867"), stats.getDiversificationScore());
        assertEquals(new BigDecimal("300.00"), summary.totalInvested());
        assertEquals(new BigDecimal("325.00"), summary.totalCurrentValue());
        assertEquals(new BigDecimal("25.00"), summary.totalPnL());
        assertEquals(new BigDecimal("8.33"), summary.totalPnLPercent());
        assertEquals(4, summary.totalHoldings());

        assertTrue(riskFlags.contains(RiskFlag.HIGH_CONCENTRATION.name()));
        assertTrue(riskFlags.contains(RiskFlag.TOP_HEAVY_PORTFOLIO.name()));
        assertTrue(riskFlags.contains("SECTOR_CONCENTRATION_TECHNOLOGY"));
        assertTrue(riskFlags.contains(RiskFlag.UNDER_DIVERSIFIED.name()));
    }

    @Test
    void calculatePortfolioStats_returnsZeroDiversificationScoreForSingleHolding() throws Exception {
        User user = new User("ZERODHA", "portfolio-1");
        setField(user, "id", 42L);

        List<UserHolding> holdings = List.of(
                holding(user, 101L, "INFY", "Technology", BigDecimal.valueOf(100), BigDecimal.valueOf(120), BigDecimal.valueOf(5), BigDecimal.valueOf(100))
        );

        PortfolioStats stats = portfolioAnalyticsService.calculatePortfolioStats(user, holdings, LocalDateTime.now());

        assertEquals(new BigDecimal("0"), stats.getDiversificationScore());
    }

    private UserHolding holding(User user,
                                Long instrumentToken,
                                String symbol,
                                String sector,
                                BigDecimal investedValue,
                                BigDecimal currentValue,
                                BigDecimal dayChange,
                                BigDecimal weightPercent) {
        Instrument instrument = new Instrument(instrumentToken, symbol, "NSE", null);
        instrument.setSector(sector);
        instrument.setMarketCapCategory("LARGE");

        UserHolding holding = new UserHolding(
                user,
                instrument,
                10,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.TEN,
                investedValue,
                currentValue,
                currentValue.subtract(investedValue),
                BigDecimal.ZERO,
                dayChange,
                BigDecimal.ZERO
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
