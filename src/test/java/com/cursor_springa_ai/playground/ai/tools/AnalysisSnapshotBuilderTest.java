package com.cursor_springa_ai.playground.ai.tools;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.analytics.model.EnrichedHoldingData;
import com.cursor_springa_ai.playground.analytics.model.PortfolioSummary;
import com.cursor_springa_ai.playground.ai.dto.AnalysisSnapshot;
import com.cursor_springa_ai.playground.ai.dto.DecisionSignals;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisSnapshotBuilderTest {

    private final PortfolioDerivedMetricsService derivedMetricsService = new PortfolioDerivedMetricsService();
    private final AnalysisSnapshotBuilder builder = new AnalysisSnapshotBuilder(
            derivedMetricsService,
            new DecisionHintsBuilder(derivedMetricsService)
    );

    @Test
    void build_populatesClassificationFromContext() {
        AnalysisSnapshot snapshot = builder.build(sampleContext());

        assertNotNull(snapshot.classification());
        assertEquals(PortfolioRiskLevel.HIGH, snapshot.classification().riskLevel());
        assertEquals(DiversificationLevel.AVERAGE, snapshot.classification().diversificationLevel());
        assertEquals(PortfolioStyle.GROWTH_HEAVY, snapshot.classification().portfolioStyle());
    }

    @Test
    void build_populatesRiskFlagsFromContext() {
        AnalysisSnapshot snapshot = builder.build(sampleContext());

        assertTrue(snapshot.riskFlags().contains(RiskFlag.HIGH_CONCENTRATION.name()));
    }

    @Test
    void build_extractsTopHoldingsSortedByAllocationDescending() {
        AnalysisSnapshot snapshot = builder.build(sampleContext());

        assertNotNull(snapshot.topHoldings());
        assertEquals(3, snapshot.topHoldings().size());
        // INFY has highest allocation (35)
        assertEquals("INFY", snapshot.topHoldings().get(0).symbol());
        assertEquals(BigDecimal.valueOf(35), snapshot.topHoldings().get(0).allocation());
        assertTrue(snapshot.topHoldings().get(0).riskFlags().contains(RiskFlag.HIGH_CONCENTRATION.name()));
    }

    @Test
    void build_limitsTopHoldingsToFive() {
        List<EnrichedHoldingData> manyHoldings = List.of(
                holding("A", BigDecimal.valueOf(25), "technology", "largecap", List.of()),
                holding("B", BigDecimal.valueOf(20), "technology", "largecap", List.of()),
                holding("C", BigDecimal.valueOf(18), "banking", "largecap", List.of()),
                holding("D", BigDecimal.valueOf(15), "banking", "midcap", List.of()),
                holding("E", BigDecimal.valueOf(12), "pharma", "midcap", List.of()),
                holding("F", BigDecimal.valueOf(10), "pharma", "smallcap", List.of())
        );
        PortfolioReasoningContext context = contextWithHoldings(manyHoldings);

        AnalysisSnapshot snapshot = builder.build(context);

        assertEquals(5, snapshot.topHoldings().size());
    }

    @Test
    void build_extractsSectorExposureAggregated() {
        AnalysisSnapshot snapshot = builder.build(sampleContext());

        assertNotNull(snapshot.sectorExposure());
        // Both INFY and TCS are "technology" â†’ combined 65%; HDFCBANK is "financials" â†’ 20%
        var techSector = snapshot.sectorExposure().stream()
                .filter(s -> "technology".equals(s.sector())).findFirst().orElseThrow();
        assertEquals(BigDecimal.valueOf(65), techSector.allocation());

        var financialsSector = snapshot.sectorExposure().stream()
                .filter(s -> "financials".equals(s.sector())).findFirst().orElseThrow();
        assertEquals(BigDecimal.valueOf(20), financialsSector.allocation());
    }

    @Test
    void build_sectorExposureSortedByAllocationDescending() {
        AnalysisSnapshot snapshot = builder.build(sampleContext());

        BigDecimal prev = BigDecimal.valueOf(Long.MAX_VALUE);
        for (var sector : snapshot.sectorExposure()) {
            assertTrue(sector.allocation().compareTo(prev) <= 0);
            prev = sector.allocation();
        }
    }

    @Test
    void build_portfolioStatsPopulatedFromContext() {
        AnalysisSnapshot snapshot = builder.build(sampleContext());

        assertNotNull(snapshot.portfolioStats());
        assertEquals(3, snapshot.portfolioStats().stockCount());
        assertEquals(BigDecimal.valueOf(35), snapshot.portfolioStats().largestHoldingPercent());
        assertEquals(BigDecimal.valueOf(65), snapshot.portfolioStats().top3Percent());
        assertEquals(BigDecimal.valueOf(12.5), snapshot.portfolioStats().pnlPercent());
    }

    @Test
    void build_includesDecisionHints() {
        AnalysisSnapshot snapshot = builder.build(sampleContext());

        assertNotNull(snapshot.decisionHints());
        assertEquals(RiskFlag.HIGH_CONCENTRATION.name(), snapshot.decisionHints().primaryRisk());
        assertEquals("INFY", snapshot.decisionHints().largestHoldingSymbol());
        assertEquals(BigDecimal.valueOf(35), snapshot.decisionHints().largestHoldingPercent());
        assertTrue(snapshot.decisionHints().concentrationReductionNeeded());
    }

    @Test
    void build_handlesEmptyHoldings() {
        PortfolioReasoningContext context = new PortfolioReasoningContext(
                "user-1",
                new PortfolioSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0),
                null,
                List.of(),
                List.of(),
                null,
                null
        );

        AnalysisSnapshot snapshot = builder.build(context);

        assertNotNull(snapshot);
        assertTrue(snapshot.topHoldings().isEmpty());
        assertTrue(snapshot.sectorExposure().isEmpty());
        assertTrue(snapshot.riskFlags().isEmpty());
    }

    // ---- DecisionSignals tests ----

    @Test
    void build_includesDecisionSignals() {
        AnalysisSnapshot snapshot = builder.build(sampleContext());

        assertNotNull(snapshot.decisionSignals());
    }

    @Test
    void build_decisionSignals_primaryRiskIsHighestPriorityFlag() {
        AnalysisSnapshot snapshot = builder.build(sampleContext());

        assertEquals(RiskFlag.HIGH_CONCENTRATION.name(), snapshot.decisionSignals().primaryRisk());
    }

    @Test
    void build_decisionSignals_primaryRiskDriverIsSymbolCausingPrimaryRisk() {
        AnalysisSnapshot snapshot = builder.build(sampleContext());

        // INFY has HIGH_CONCENTRATION flag and is the largest holding
        assertEquals("INFY", snapshot.decisionSignals().primaryRiskDriver());
    }

    @Test
    void build_decisionSignals_largestHoldingSymbolAndPercent() {
        AnalysisSnapshot snapshot = builder.build(sampleContext());

        assertEquals("INFY", snapshot.decisionSignals().largestHoldingSymbol());
        assertEquals(BigDecimal.valueOf(35), snapshot.decisionSignals().largestHoldingPercent());
    }

    @Test
    void build_decisionSignals_priorityActionsNotEmpty() {
        AnalysisSnapshot snapshot = builder.build(sampleContext());

        assertNotNull(snapshot.decisionSignals().priorityActions());
        assertFalse(snapshot.decisionSignals().priorityActions().isEmpty());
    }

    @Test
    void build_decisionSignals_priorityActionsMentionsLargestHolding() {
        AnalysisSnapshot snapshot = builder.build(sampleContext());

        assertTrue(snapshot.decisionSignals().priorityActions().stream()
                .anyMatch(a -> a.contains("INFY")));
    }

    @Test
    void build_decisionSignals_priorityActionsLimitedToThree() {
        // Context with 4+ different risk flags
        PortfolioReasoningContext context = new PortfolioReasoningContext(
                "portfolio-1",
                new PortfolioSummary(BigDecimal.valueOf(100000), BigDecimal.valueOf(110000),
                        BigDecimal.valueOf(10000), BigDecimal.TEN, 2),
                null,
                List.of(RiskFlag.HIGH_CONCENTRATION.name(),
                        RiskFlag.UNDER_DIVERSIFIED.name(),
                        RiskFlag.TOP_HEAVY_PORTFOLIO.name(),
                        RiskFlag.HIGH_VALUATION.name()),
                List.of(),
                null,
                null
        );

        AnalysisSnapshot snapshot = builder.build(context);

        assertTrue(snapshot.decisionSignals().priorityActions().size() <= 3);
    }

    @Test
    void build_decisionSignals_riskDriversByFlagMapsHoldingFlags() {
        AnalysisSnapshot snapshot = builder.build(sampleContext());

        // INFY has HIGH_CONCENTRATION, TCS has HIGH_VALUATION
        assertTrue(snapshot.decisionSignals().riskDriversByFlag()
                .containsKey(RiskFlag.HIGH_CONCENTRATION.name()));
        assertTrue(snapshot.decisionSignals().riskDriversByFlag()
                .get(RiskFlag.HIGH_CONCENTRATION.name()).contains("INFY"));

        assertTrue(snapshot.decisionSignals().riskDriversByFlag()
                .containsKey(RiskFlag.HIGH_VALUATION.name()));
        assertTrue(snapshot.decisionSignals().riskDriversByFlag()
                .get(RiskFlag.HIGH_VALUATION.name()).contains("TCS"));
    }

    @Test
    void build_decisionSignals_emptyWhenNoHoldingsAndNoFlags() {
        PortfolioReasoningContext context = new PortfolioReasoningContext(
                "user-1",
                new PortfolioSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0),
                null,
                List.of(),
                List.of(),
                null,
                null
        );

        DecisionSignals signals = builder.build(context).decisionSignals();

        assertNotNull(signals);
        assertNull(signals.primaryRisk());
        assertNull(signals.primaryRiskDriver());
        assertNull(signals.largestHoldingSymbol());
        assertNull(signals.largestHoldingPercent());
        assertTrue(signals.priorityActions().isEmpty());
        assertTrue(signals.riskDriversByFlag().isEmpty());
    }

    @Test
    void build_decisionSignals_riskDriversByFlagIsImmutable() {
        AnalysisSnapshot snapshot = builder.build(sampleContext());

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.decisionSignals().riskDriversByFlag().put("EXTRA", List.of()));
    }

    @Test
    void build_decisionSignals_priorityActionsIsImmutable() {
        AnalysisSnapshot snapshot = builder.build(sampleContext());

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.decisionSignals().priorityActions().add("extra action"));
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

        EnrichedHoldingData infy = holding("INFY", BigDecimal.valueOf(35), "technology", "largecap",
                List.of(RiskFlag.HIGH_CONCENTRATION.name()));
        EnrichedHoldingData tcs = holding("TCS", BigDecimal.valueOf(30), "technology", "largecap",
                List.of(RiskFlag.HIGH_VALUATION.name()));
        EnrichedHoldingData hdfcBank = holding("HDFCBANK", BigDecimal.valueOf(20), "financials", "largecap",
                List.of());

        return new PortfolioReasoningContext("portfolio-1", summary, stats,
                List.of(RiskFlag.HIGH_CONCENTRATION.name()),
                List.of(infy, tcs, hdfcBank),
                new PortfolioClassification(PortfolioRiskLevel.HIGH, DiversificationLevel.AVERAGE,
                        ConcentrationLevel.CONCENTRATED, PerformanceLevel.GOOD,
                        PortfolioStyle.GROWTH_HEAVY, BigDecimal.valueOf(5), BigDecimal.valueOf(65)),
                null);
    }

    private PortfolioReasoningContext contextWithHoldings(List<EnrichedHoldingData> holdings) {
        PortfolioSummary summary = new PortfolioSummary(
                BigDecimal.valueOf(100000), BigDecimal.valueOf(110000),
                BigDecimal.valueOf(10000), BigDecimal.TEN, holdings.size());
        PortfolioStats stats = new PortfolioStats(1L,
                BigDecimal.valueOf(100000), BigDecimal.valueOf(110000),
                BigDecimal.valueOf(10000), BigDecimal.TEN,
                BigDecimal.valueOf(25), holdings.size(),
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(55), BigDecimal.valueOf(50), null);
                return new PortfolioReasoningContext("portfolio-1", summary, stats, List.of(), holdings, null, null);
    }

    private EnrichedHoldingData holding(String symbol, BigDecimal allocation,
                                        String sector, String marketCapType, List<String> riskFlags) {
        return new EnrichedHoldingData(
                symbol, "equity", BigDecimal.TEN,
                BigDecimal.valueOf(1000), BigDecimal.valueOf(1100),
                BigDecimal.valueOf(10000), BigDecimal.valueOf(11000),
                BigDecimal.valueOf(1000),
                sector, BigDecimal.valueOf(20), BigDecimal.ONE,
                BigDecimal.valueOf(18), BigDecimal.valueOf(1200), BigDecimal.valueOf(800),
                marketCapType, BigDecimal.valueOf(950),
                allocation, BigDecimal.TEN, BigDecimal.valueOf(-5),
                "FAIRLY_VALUED", BigDecimal.valueOf(90), BigDecimal.valueOf(2),
                riskFlags);
    }
}
