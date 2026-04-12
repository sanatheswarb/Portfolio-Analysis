package com.cursor_springa_ai.playground.ai.tools;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.analytics.model.EnrichedHoldingData;
import com.cursor_springa_ai.playground.analytics.model.PortfolioSummary;
import com.cursor_springa_ai.playground.ai.dto.FlaggedHoldingDto;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.entity.PortfolioStats;
import com.cursor_springa_ai.playground.model.enums.RiskFlag;
import com.cursor_springa_ai.playground.model.enums.ConcentrationLevel;
import com.cursor_springa_ai.playground.model.enums.DiversificationLevel;
import com.cursor_springa_ai.playground.model.enums.PerformanceLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioRiskLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioStyle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlaggedHoldingsBuilderTest {

    private final FlaggedHoldingsBuilder builder = new FlaggedHoldingsBuilder();

    @Test
    void build_sortsByAllocationDescending() {
        List<FlaggedHoldingDto> result = builder.build(sampleContext());

        assertEquals("INFY", result.get(0).symbol());
        assertEquals("TCS", result.get(1).symbol());
        assertEquals("HDFCBANK", result.get(2).symbol());
    }

    @Test
    void build_includesHoldingsWithSignificantAllocation() {
        List<FlaggedHoldingDto> result = builder.build(sampleContext());

        // HDFCBANK has 20% allocation (> 10 threshold) but no risk flags â€” still included
        assertTrue(result.stream().anyMatch(h -> "HDFCBANK".equals(h.symbol())));
    }

    @Test
    void build_excludesNonTopHoldingBelowThresholdWithNoFlags() {
        EnrichedHoldingData topA = holding("A", BigDecimal.valueOf(12), List.of());
        EnrichedHoldingData topB = holding("B", BigDecimal.valueOf(11), List.of());
        EnrichedHoldingData topC = holding("C", BigDecimal.valueOf(9), List.of());
        EnrichedHoldingData minor = holding("MINOR", BigDecimal.valueOf(3), List.of());
        PortfolioReasoningContext context = contextWithHoldings(List.of(topA, topB, topC, minor));

        List<FlaggedHoldingDto> result = builder.build(context);

        assertEquals(3, result.size());
        assertTrue(result.stream().noneMatch(h -> "MINOR".equals(h.symbol())));
    }

    @Test
    void build_includesHoldingWithRiskFlagEvenIfLowAllocation() {
        EnrichedHoldingData lowAllocationFlagged = holding("FLAGGED", BigDecimal.valueOf(3),
                List.of(RiskFlag.DEEP_CORRECTION.name()));
        PortfolioReasoningContext context = contextWithHoldings(List.of(lowAllocationFlagged));

        List<FlaggedHoldingDto> result = builder.build(context);

        assertEquals(1, result.size());
        assertEquals("FLAGGED", result.get(0).symbol());
    }

    @Test
    void build_includesTopThreeHoldingsEvenWithoutFlagsAndThreshold() {
        List<EnrichedHoldingData> holdings = List.of(
                holding("A", BigDecimal.valueOf(9), List.of()),
                holding("B", BigDecimal.valueOf(8), List.of()),
                holding("C", BigDecimal.valueOf(7), List.of()),
                holding("D", BigDecimal.valueOf(6), List.of())
        );

        List<FlaggedHoldingDto> result = builder.build(contextWithHoldings(holdings));

        assertEquals(3, result.size());
        assertEquals("A", result.get(0).symbol());
        assertEquals("B", result.get(1).symbol());
        assertEquals("C", result.get(2).symbol());
    }

    @Test
    void build_limitsToFiveHoldings() {
        List<EnrichedHoldingData> many = List.of(
                holding("A", BigDecimal.valueOf(25), List.of()),
                holding("B", BigDecimal.valueOf(20), List.of()),
                holding("C", BigDecimal.valueOf(15), List.of()),
                holding("D", BigDecimal.valueOf(12), List.of()),
                holding("E", BigDecimal.valueOf(11), List.of()),
                holding("F", BigDecimal.valueOf(10.5), List.of())
        );
        PortfolioReasoningContext context = contextWithHoldings(many);

        List<FlaggedHoldingDto> result = builder.build(context);

        assertEquals(5, result.size());
    }

    @Test
    void build_classifiesImportanceCorrectly() {
        List<FlaggedHoldingDto> result = builder.build(sampleContext());

        FlaggedHoldingDto infy = result.stream().filter(h -> "INFY".equals(h.symbol())).findFirst().orElseThrow();
        FlaggedHoldingDto hdfcBank = result.stream().filter(h -> "HDFCBANK".equals(h.symbol())).findFirst().orElseThrow();

        assertEquals("CORE", infy.importance());          // 35% > 20
        assertEquals("SIGNIFICANT", hdfcBank.importance()); // 20% not > 20, but > 10
    }

    @Test
    void build_classifiesPerformanceStatus() {
        List<FlaggedHoldingDto> result = builder.build(sampleContext());

        FlaggedHoldingDto infy = result.stream().filter(h -> "INFY".equals(h.symbol())).findFirst().orElseThrow();
        assertEquals("PROFIT", infy.performanceStatus()); // profitPercent = 16.67

        EnrichedHoldingData loser = holding("LOSER", BigDecimal.valueOf(15), List.of());
        EnrichedHoldingData loserWithLoss = new EnrichedHoldingData(
                loser.symbol(), loser.assetType(), loser.quantity(), loser.averageBuyPrice(),
                loser.currentPrice(), loser.investedValue(), loser.currentValue(), loser.profitLoss(),
                loser.sector(), loser.pe(), loser.beta(), loser.sectorPe(), loser.week52High(),
                loser.week52Low(), loser.marketCapType(), loser.dma200(), loser.allocationPercent(),
                BigDecimal.valueOf(-10), loser.distanceFromHigh(), loser.valuationFlag(),
                loser.momentumScore(), loser.riskScore(), loser.riskFlags()
        );
        List<FlaggedHoldingDto> loserResult = builder.build(contextWithHoldings(List.of(loserWithLoss)));
        assertEquals("LOSS", loserResult.get(0).performanceStatus());
    }

    @Test
    void build_classifiesRiskSeverity() {
        EnrichedHoldingData highRisk = holdingWithFlags("HIGH", BigDecimal.valueOf(15),
                List.of("HIGH_CONCENTRATION", "HIGH_VALUATION", "DEEP_CORRECTION"));
        EnrichedHoldingData moderateRisk = holdingWithFlags("MODERATE", BigDecimal.valueOf(12),
                List.of("HIGH_CONCENTRATION", "HIGH_VALUATION"));
        EnrichedHoldingData lowRisk = holdingWithFlags("LOW", BigDecimal.valueOf(11),
                List.of("PROFIT_BOOKING_ZONE"));

        List<FlaggedHoldingDto> result = builder.build(contextWithHoldings(
                List.of(highRisk, moderateRisk, lowRisk)));

        FlaggedHoldingDto h = result.stream().filter(dto -> "HIGH".equals(dto.symbol())).findFirst().orElseThrow();
        FlaggedHoldingDto m = result.stream().filter(dto -> "MODERATE".equals(dto.symbol())).findFirst().orElseThrow();
        FlaggedHoldingDto l = result.stream().filter(dto -> "LOW".equals(dto.symbol())).findFirst().orElseThrow();

        assertEquals("HIGH", h.riskSeverity());
        assertEquals("MODERATE", m.riskSeverity());
        assertEquals("LOW", l.riskSeverity());
    }

    @Test
    void build_determinesPrimaryConcernByPriority() {
        EnrichedHoldingData holding = holdingWithFlags("X", BigDecimal.valueOf(15),
                List.of("HIGH_VALUATION", "HIGH_CONCENTRATION"));

        List<FlaggedHoldingDto> result = builder.build(contextWithHoldings(List.of(holding)));

        assertEquals("HIGH_CONCENTRATION", result.get(0).primaryConcern());
    }

    @Test
    void build_primaryConcernIsNullWhenNoFlags() {
        List<FlaggedHoldingDto> result = builder.build(sampleContext());

        FlaggedHoldingDto hdfcBank = result.stream().filter(h -> "HDFCBANK".equals(h.symbol())).findFirst().orElseThrow();
        assertNull(hdfcBank.primaryConcern());
    }

    @Test
    void build_attentionReasonsReflectDataConditions() {
        List<FlaggedHoldingDto> result = builder.build(sampleContext());

        FlaggedHoldingDto infy = result.stream().filter(h -> "INFY".equals(h.symbol())).findFirst().orElseThrow();
        // INFY: allocationPercent=35 (>20), valuationFlag=OVERVALUED, distanceFromHigh=-7.89 (>-10)
        assertTrue(infy.attentionReasons().contains("Largest portfolio allocation"));
        assertTrue(infy.attentionReasons().contains("Valuation above sector average"));
        assertTrue(infy.attentionReasons().contains("Trading near 52 week high"));
    }

    @Test
    void build_attentionReasonsIncludeDeepCorrectionReason() {
        EnrichedHoldingData crashed = holdingWithDistanceFromHigh("CRASH", BigDecimal.valueOf(15),
                BigDecimal.valueOf(-30));

        List<FlaggedHoldingDto> result = builder.build(contextWithHoldings(List.of(crashed)));

        assertTrue(result.get(0).attentionReasons().contains("Trading well below 52 week high"));
    }

    @Test
    void build_attentionReasonsIncludeLossReason() {
        EnrichedHoldingData loser = holdingWithProfitPercent("LOSS_STOCK", BigDecimal.valueOf(15),
                BigDecimal.valueOf(-15));

        List<FlaggedHoldingDto> result = builder.build(contextWithHoldings(List.of(loser)));

        assertTrue(result.get(0).attentionReasons().contains("Position is currently at a loss"));
    }

    // ---- helpers ----

    private PortfolioReasoningContext sampleContext() {
        PortfolioSummary summary = new PortfolioSummary(
                BigDecimal.valueOf(100000), BigDecimal.valueOf(112500),
                BigDecimal.valueOf(12500), BigDecimal.valueOf(12.5), 3);
        PortfolioStats stats = new PortfolioStats(1L,
                BigDecimal.valueOf(100000), BigDecimal.valueOf(112500),
                BigDecimal.valueOf(12500), BigDecimal.valueOf(12.5),
                BigDecimal.valueOf(35), 3,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(65), BigDecimal.valueOf(48), null);

        EnrichedHoldingData infy = new EnrichedHoldingData(
                "INFY", "equity", BigDecimal.TEN, BigDecimal.valueOf(1500), BigDecimal.valueOf(1750),
                BigDecimal.valueOf(15000), BigDecimal.valueOf(17500), BigDecimal.valueOf(2500),
                "technology", BigDecimal.valueOf(28), BigDecimal.valueOf(1.1), BigDecimal.valueOf(22),
                BigDecimal.valueOf(1900), BigDecimal.valueOf(1100), "largecap", BigDecimal.valueOf(1400),
                BigDecimal.valueOf(35), BigDecimal.valueOf(16.67), BigDecimal.valueOf(-7.89),
                "OVERVALUED", BigDecimal.valueOf(92.11), BigDecimal.valueOf(4),
                List.of(RiskFlag.HIGH_CONCENTRATION.name()));

        EnrichedHoldingData tcs = new EnrichedHoldingData(
                "TCS", "equity", BigDecimal.valueOf(12), BigDecimal.valueOf(3200), BigDecimal.valueOf(3333),
                BigDecimal.valueOf(38400), BigDecimal.valueOf(39996), BigDecimal.valueOf(1596),
                "technology", BigDecimal.valueOf(26), BigDecimal.valueOf(0.9), BigDecimal.valueOf(22),
                BigDecimal.valueOf(3600), BigDecimal.valueOf(2900), "largecap", BigDecimal.valueOf(3100),
                BigDecimal.valueOf(30), BigDecimal.valueOf(4.16), BigDecimal.valueOf(-7.42),
                "FAIRLY_VALUED", BigDecimal.valueOf(92.58), BigDecimal.valueOf(3),
                List.of(RiskFlag.HIGH_VALUATION.name()));

        EnrichedHoldingData hdfcBank = new EnrichedHoldingData(
                "HDFCBANK", "equity", BigDecimal.valueOf(8), BigDecimal.valueOf(1450), BigDecimal.valueOf(1500),
                BigDecimal.valueOf(11600), BigDecimal.valueOf(12000), BigDecimal.valueOf(400),
                "financials", BigDecimal.valueOf(19), BigDecimal.valueOf(0.8), BigDecimal.valueOf(20),
                BigDecimal.valueOf(1800), BigDecimal.valueOf(1200), "largecap", BigDecimal.valueOf(1420),
                BigDecimal.valueOf(20), BigDecimal.valueOf(3.45), BigDecimal.valueOf(-16.67),
                "FAIRLY_VALUED", BigDecimal.valueOf(83.33), BigDecimal.valueOf(2), List.of());

        return new PortfolioReasoningContext("portfolio-1", summary, stats,
                List.of(RiskFlag.HIGH_CONCENTRATION.name()), List.of(infy, tcs, hdfcBank),
                new PortfolioClassification(PortfolioRiskLevel.HIGH, DiversificationLevel.AVERAGE,
                        ConcentrationLevel.CONCENTRATED, PerformanceLevel.GOOD,
                        PortfolioStyle.GROWTH_HEAVY, BigDecimal.ZERO, BigDecimal.valueOf(65)),
                null);
    }

    private PortfolioReasoningContext contextWithHoldings(List<EnrichedHoldingData> holdings) {
        return new PortfolioReasoningContext("test", null, null, List.of(), holdings, null, null);
    }

    private EnrichedHoldingData holding(String symbol, BigDecimal allocation, List<String> flags) {
        return new EnrichedHoldingData(symbol, "equity", BigDecimal.ONE, BigDecimal.valueOf(100),
                BigDecimal.valueOf(110), BigDecimal.valueOf(100), BigDecimal.valueOf(110),
                BigDecimal.valueOf(10), "technology", null, null, null, null, null,
                "largecap", null, allocation, BigDecimal.valueOf(10),
                BigDecimal.valueOf(-15), "FAIRLY_VALUED", BigDecimal.valueOf(85),
                BigDecimal.valueOf(2), flags);
    }

    private EnrichedHoldingData holdingWithFlags(String symbol, BigDecimal allocation, List<String> flags) {
        return holding(symbol, allocation, flags);
    }

    private EnrichedHoldingData holdingWithDistanceFromHigh(String symbol, BigDecimal allocation,
                                                            BigDecimal distanceFromHigh) {
        return new EnrichedHoldingData(symbol, "equity", BigDecimal.ONE, BigDecimal.valueOf(100),
                BigDecimal.valueOf(70), BigDecimal.valueOf(100), BigDecimal.valueOf(70),
                BigDecimal.valueOf(-30), "technology", null, null, null, null, null,
                "largecap", null, allocation, BigDecimal.valueOf(-30),
                distanceFromHigh, "FAIRLY_VALUED", BigDecimal.valueOf(70),
                BigDecimal.valueOf(3), List.of());
    }

    private EnrichedHoldingData holdingWithProfitPercent(String symbol, BigDecimal allocation,
                                                         BigDecimal profitPercent) {
        return new EnrichedHoldingData(symbol, "equity", BigDecimal.ONE, BigDecimal.valueOf(100),
                BigDecimal.valueOf(85), BigDecimal.valueOf(100), BigDecimal.valueOf(85),
                BigDecimal.valueOf(-15), "technology", null, null, null, null, null,
                "largecap", null, allocation, profitPercent,
                BigDecimal.valueOf(-15), "FAIRLY_VALUED", BigDecimal.valueOf(85),
                BigDecimal.valueOf(2), List.of());
    }
}
