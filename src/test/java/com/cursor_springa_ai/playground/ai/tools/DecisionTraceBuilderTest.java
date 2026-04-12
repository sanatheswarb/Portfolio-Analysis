package com.cursor_springa_ai.playground.ai.tools;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.analytics.model.EnrichedHoldingData;
import com.cursor_springa_ai.playground.analytics.model.PortfolioSummary;
import com.cursor_springa_ai.playground.ai.dto.AnalysisDecisionTrace;
import com.cursor_springa_ai.playground.analytics.PortfolioDerivedMetricsService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionTraceBuilderTest {

    private final PortfolioDerivedMetricsService derivedMetricsService = new PortfolioDerivedMetricsService();
    private final DecisionTraceBuilder builder = new DecisionTraceBuilder(derivedMetricsService);

    @Test
    void build_populatesPrimaryRiskFromHighestPriorityFlag() {
        PortfolioReasoningContext context = sampleContext();

        AnalysisDecisionTrace trace = builder.build(context);

        assertEquals(RiskFlag.HIGH_CONCENTRATION.name(), trace.primaryRisk());
    }

    @Test
    void build_populatesPrimaryRiskDriverAsLargestHolding() {
        PortfolioReasoningContext context = sampleContext();

        AnalysisDecisionTrace trace = builder.build(context);

        assertEquals("INFY", trace.primaryRiskDriver());
    }

    @Test
    void build_topRiskDriversContainsFlaggedSymbols() {
        PortfolioReasoningContext context = sampleContext();

        AnalysisDecisionTrace trace = builder.build(context);

        assertNotNull(trace.topRiskDrivers());
        assertTrue(trace.topRiskDrivers().contains("INFY"));
        assertTrue(trace.topRiskDrivers().contains("TCS"));
    }

    @Test
    void build_mainDiversificationIssueDescribesConcentration() {
        PortfolioReasoningContext context = sampleContext();

        AnalysisDecisionTrace trace = builder.build(context);

        assertNotNull(trace.mainDiversificationIssue());
        assertTrue(trace.mainDiversificationIssue().contains("Top 3 holdings exceed"));
    }

    @Test
    void build_mainStrengthReportsProfitability() {
        PortfolioReasoningContext context = sampleContext();

        AnalysisDecisionTrace trace = builder.build(context);

        assertEquals("Portfolio profitable", trace.mainStrength());
    }

    @Test
    void build_nullPrimaryRiskWhenNoRiskFlags() {
        PortfolioReasoningContext context = new PortfolioReasoningContext(
                "user-1",
                new PortfolioSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0),
                null, List.of(), List.of(), null, null);

        AnalysisDecisionTrace trace = builder.build(context);

        assertNull(trace.primaryRisk());
        assertNull(trace.primaryRiskDriver());
        assertTrue(trace.topRiskDrivers().isEmpty());
    }

    @Test
    void build_mainStrengthNullWhenPortfolioLoss() {
        PortfolioStats stats = new PortfolioStats(1L,
                BigDecimal.valueOf(100000), BigDecimal.valueOf(90000),
                BigDecimal.valueOf(-10000), BigDecimal.valueOf(-10),
                BigDecimal.valueOf(35), 3,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(65), BigDecimal.valueOf(48), null);
        PortfolioClassification classification = new PortfolioClassification(
                PortfolioRiskLevel.HIGH, DiversificationLevel.AVERAGE,
                ConcentrationLevel.CONCENTRATED, PerformanceLevel.WEAK,
                PortfolioStyle.GROWTH_HEAVY, BigDecimal.valueOf(5), BigDecimal.valueOf(65));
        PortfolioReasoningContext context = new PortfolioReasoningContext(
                "user-1",
                new PortfolioSummary(BigDecimal.valueOf(100000), BigDecimal.valueOf(90000),
                        BigDecimal.valueOf(-10000), BigDecimal.valueOf(-10), 3),
                stats, List.of(), List.of(), classification, null);

        AnalysisDecisionTrace trace = builder.build(context);

        assertNull(trace.mainStrength());
    }

    @Test
    void build_topRiskDriversIsDefensiveCopy() {
        PortfolioReasoningContext context = sampleContext();

        AnalysisDecisionTrace trace = builder.build(context);

        List<String> drivers = trace.topRiskDrivers();
        assertNotNull(drivers);
        assertThrows(UnsupportedOperationException.class, () -> drivers.add("extra"));
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
