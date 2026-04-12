package com.cursor_springa_ai.playground.analytics;

import com.cursor_springa_ai.playground.analytics.model.EnrichedHoldingData;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.entity.PortfolioStats;
import com.cursor_springa_ai.playground.model.enums.ConcentrationLevel;
import com.cursor_springa_ai.playground.model.enums.DiversificationLevel;
import com.cursor_springa_ai.playground.model.enums.PerformanceLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioRiskLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioStyle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PortfolioClassificationServiceTest {

    private final PortfolioClassificationService service = new PortfolioClassificationService();

    @Test
    void classify_nullStats_returnsEmptyClassification() {
        PortfolioClassification result = service.classify(null, List.of(sampleHolding("INFY", "LARGE", null, null)));

        assertEquals(PortfolioRiskLevel.LOW, result.riskLevel());
        assertEquals(DiversificationLevel.POOR, result.diversificationLevel());
        assertEquals(ConcentrationLevel.BALANCED, result.concentrationLevel());
        assertEquals(PerformanceLevel.STABLE, result.performanceLevel());
        assertEquals(PortfolioStyle.MIXED, result.portfolioStyle());
        assertEquals(BigDecimal.ZERO, result.smallCapExposure());
        assertEquals(BigDecimal.ZERO, result.top3Exposure());
    }

    @Test
    void classify_emptyHoldings_returnsEmptyClassification() {
        PortfolioClassification result = service.classify(sampleStats(10, 15.0, 40.0, 5.0), List.of());

        assertEquals(PortfolioRiskLevel.LOW, result.riskLevel());
        assertEquals(DiversificationLevel.POOR, result.diversificationLevel());
    }

    @Test
    void classifyRisk_highLargestWeight_returnsHigh() {
        PortfolioStats stats = sampleStats(5, 35.0, 50.0, 5.0);
        PortfolioClassification result = service.classify(stats, List.of(sampleHolding("A", "LARGE", null, null)));

        assertEquals(PortfolioRiskLevel.HIGH, result.riskLevel());
    }

    @Test
    void classifyRisk_highTop3_returnsHigh() {
        PortfolioStats stats = sampleStats(5, 10.0, 75.0, 5.0);
        PortfolioClassification result = service.classify(stats, List.of(sampleHolding("A", "LARGE", null, null)));

        assertEquals(PortfolioRiskLevel.HIGH, result.riskLevel());
    }

    @Test
    void classifyRisk_highSmallCap_returnsHigh() {
        PortfolioStats stats = sampleStats(5, 10.0, 40.0, 5.0);
        EnrichedHoldingData smallCap1 = sampleHolding("A", "SMALL", null, null, BigDecimal.valueOf(25));
        EnrichedHoldingData smallCap2 = sampleHolding("B", "SMALL", null, null, BigDecimal.valueOf(20));
        PortfolioClassification result = service.classify(stats, List.of(smallCap1, smallCap2));

        assertEquals(PortfolioRiskLevel.HIGH, result.riskLevel());
    }

    @Test
    void classifyRisk_moderateLargestWeight_returnsModerate() {
        PortfolioStats stats = sampleStats(5, 22.0, 40.0, 5.0);
        PortfolioClassification result = service.classify(stats, List.of(sampleHolding("A", "LARGE", null, null)));

        assertEquals(PortfolioRiskLevel.MODERATE, result.riskLevel());
    }

    @Test
    void classifyRisk_lowValues_returnsLow() {
        PortfolioStats stats = sampleStats(10, 10.0, 30.0, 5.0);
        PortfolioClassification result = service.classify(stats, List.of(sampleHolding("A", "LARGE", null, null)));

        assertEquals(PortfolioRiskLevel.LOW, result.riskLevel());
    }

    @Test
    void classifyDiversification_fewStocks_returnsPoor() {
        PortfolioStats stats = sampleStats(3, 10.0, 30.0, 5.0);
        PortfolioClassification result = service.classify(stats, List.of(sampleHolding("A", "LARGE", null, null)));

        assertEquals(DiversificationLevel.POOR, result.diversificationLevel());
    }

    @Test
    void classifyDiversification_moderateStocks_returnsAverage() {
        PortfolioStats stats = sampleStats(8, 10.0, 30.0, 5.0);
        PortfolioClassification result = service.classify(stats, List.of(sampleHolding("A", "LARGE", null, null)));

        assertEquals(DiversificationLevel.AVERAGE, result.diversificationLevel());
    }

    @Test
    void classifyDiversification_manyStocks_returnsGood() {
        PortfolioStats stats = sampleStats(15, 10.0, 30.0, 5.0);
        PortfolioClassification result = service.classify(stats, List.of(sampleHolding("A", "LARGE", null, null)));

        assertEquals(DiversificationLevel.GOOD, result.diversificationLevel());
    }

    @Test
    void classifyConcentration_highLargest_returnsConcentrated() {
        PortfolioStats stats = sampleStats(10, 30.0, 50.0, 5.0);
        PortfolioClassification result = service.classify(stats, List.of(sampleHolding("A", "LARGE", null, null)));

        assertEquals(ConcentrationLevel.CONCENTRATED, result.concentrationLevel());
    }

    @Test
    void classifyConcentration_moderateLargest_returnsModerate() {
        PortfolioStats stats = sampleStats(10, 18.0, 50.0, 5.0);
        PortfolioClassification result = service.classify(stats, List.of(sampleHolding("A", "LARGE", null, null)));

        assertEquals(ConcentrationLevel.MODERATE, result.concentrationLevel());
    }

    @Test
    void classifyConcentration_lowLargest_returnsBalanced() {
        PortfolioStats stats = sampleStats(10, 10.0, 30.0, 5.0);
        PortfolioClassification result = service.classify(stats, List.of(sampleHolding("A", "LARGE", null, null)));

        assertEquals(ConcentrationLevel.BALANCED, result.concentrationLevel());
    }

    @Test
    void classifyPerformance_negativePnl_returnsWeak() {
        PortfolioStats stats = sampleStats(10, 10.0, 30.0, -5.0);
        PortfolioClassification result = service.classify(stats, List.of(sampleHolding("A", "LARGE", null, null)));

        assertEquals(PerformanceLevel.WEAK, result.performanceLevel());
    }

    @Test
    void classifyPerformance_lowPnl_returnsStable() {
        PortfolioStats stats = sampleStats(10, 10.0, 30.0, 5.0);
        PortfolioClassification result = service.classify(stats, List.of(sampleHolding("A", "LARGE", null, null)));

        assertEquals(PerformanceLevel.STABLE, result.performanceLevel());
    }

    @Test
    void classifyPerformance_goodPnl_returnsGood() {
        PortfolioStats stats = sampleStats(10, 10.0, 30.0, 12.0);
        PortfolioClassification result = service.classify(stats, List.of(sampleHolding("A", "LARGE", null, null)));

        assertEquals(PerformanceLevel.GOOD, result.performanceLevel());
    }

    @Test
    void classifyPerformance_highPnl_returnsStrong() {
        PortfolioStats stats = sampleStats(10, 10.0, 30.0, 25.0);
        PortfolioClassification result = service.classify(stats, List.of(sampleHolding("A", "LARGE", null, null)));

        assertEquals(PerformanceLevel.STRONG, result.performanceLevel());
    }

    @Test
    void classifyStyle_majorityMomentum_returnsMomentumHeavy() {
        PortfolioStats stats = sampleStats(5, 10.0, 30.0, 5.0);
        EnrichedHoldingData h1 = sampleHolding("A", "LARGE", null, BigDecimal.valueOf(80));
        EnrichedHoldingData h2 = sampleHolding("B", "LARGE", null, BigDecimal.valueOf(90));
        EnrichedHoldingData h3 = sampleHolding("C", "LARGE", null, BigDecimal.valueOf(50));

        PortfolioClassification result = service.classify(stats, List.of(h1, h2, h3));

        assertEquals(PortfolioStyle.MOMENTUM_HEAVY, result.portfolioStyle());
    }

    @Test
    void classifyStyle_moreGrowth_returnsGrowthHeavy() {
        PortfolioStats stats = sampleStats(5, 10.0, 30.0, 5.0);
        EnrichedHoldingData h1 = sampleHolding("A", "LARGE", "OVERVALUED", null);
        EnrichedHoldingData h2 = sampleHolding("B", "LARGE", "OVERVALUED", null);
        EnrichedHoldingData h3 = sampleHolding("C", "LARGE", "UNDERVALUED", null);

        PortfolioClassification result = service.classify(stats, List.of(h1, h2, h3));

        assertEquals(PortfolioStyle.GROWTH_HEAVY, result.portfolioStyle());
    }

    @Test
    void classifyStyle_moreValue_returnsValueHeavy() {
        PortfolioStats stats = sampleStats(5, 10.0, 30.0, 5.0);
        EnrichedHoldingData h1 = sampleHolding("A", "LARGE", "UNDERVALUED", null);
        EnrichedHoldingData h2 = sampleHolding("B", "LARGE", "UNDERVALUED", null);
        EnrichedHoldingData h3 = sampleHolding("C", "LARGE", "OVERVALUED", null);

        PortfolioClassification result = service.classify(stats, List.of(h1, h2, h3));

        assertEquals(PortfolioStyle.VALUE_HEAVY, result.portfolioStyle());
    }

    @Test
    void classifyStyle_mixed_returnsMixed() {
        PortfolioStats stats = sampleStats(5, 10.0, 30.0, 5.0);
        EnrichedHoldingData h1 = sampleHolding("A", "LARGE", "UNDERVALUED", null);
        EnrichedHoldingData h2 = sampleHolding("B", "LARGE", "OVERVALUED", null);

        PortfolioClassification result = service.classify(stats, List.of(h1, h2));

        assertEquals(PortfolioStyle.MIXED, result.portfolioStyle());
    }

    @Test
    void smallCapExposure_calculatedCorrectly() {
        PortfolioStats stats = sampleStats(5, 10.0, 30.0, 5.0);
        EnrichedHoldingData small1 = sampleHolding("A", "SMALL", null, null, BigDecimal.valueOf(15));
        EnrichedHoldingData small2 = sampleHolding("B", "small", null, null, BigDecimal.valueOf(10));
        EnrichedHoldingData large = sampleHolding("C", "LARGE", null, null, BigDecimal.valueOf(75));

        PortfolioClassification result = service.classify(stats, List.of(small1, small2, large));

        assertNotNull(result.smallCapExposure());
        assertEquals(0, BigDecimal.valueOf(25).compareTo(result.smallCapExposure()));
    }

    @Test
    void top3Exposure_takenFromStats() {
        PortfolioStats stats = sampleStats(5, 10.0, 45.0, 5.0);
        PortfolioClassification result = service.classify(stats, List.of(sampleHolding("A", "LARGE", null, null)));

        assertEquals(0, BigDecimal.valueOf(45.0).compareTo(result.top3Exposure()));
    }

    // --- helpers ---

    private PortfolioStats sampleStats(int stockCount, double largestWeight, double top3Percent, double pnlPercent) {
        return new PortfolioStats(
                1L,
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(110000),
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(pnlPercent),
                BigDecimal.valueOf(largestWeight),
                stockCount,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(top3Percent),
                BigDecimal.valueOf(0.7),
                LocalDateTime.now()
        );
    }

    private EnrichedHoldingData sampleHolding(String symbol, String marketCapType,
                                              String valuationFlag, BigDecimal momentumScore) {
        return sampleHolding(symbol, marketCapType, valuationFlag, momentumScore, BigDecimal.valueOf(10));
    }

    private EnrichedHoldingData sampleHolding(String symbol, String marketCapType,
                                              String valuationFlag, BigDecimal momentumScore,
                                              BigDecimal allocationPercent) {
        return new EnrichedHoldingData(
                symbol, "STOCK", BigDecimal.ONE, BigDecimal.valueOf(100), BigDecimal.valueOf(110),
                BigDecimal.valueOf(100), BigDecimal.valueOf(110), BigDecimal.valueOf(10),
                "IT", null, null, null, null, null,
                marketCapType, null, allocationPercent, BigDecimal.TEN, null,
                valuationFlag, momentumScore, null, List.of()
        );
    }
}
