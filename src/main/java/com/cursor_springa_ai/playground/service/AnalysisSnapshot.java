package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.PortfolioStats;

import java.math.BigDecimal;
import java.util.List;

public record AnalysisSnapshot(
        String portfolioUserId,
        PortfolioSummary portfolioSummary,
        PortfolioClassification classification,
        List<String> portfolioRiskFlags,
        List<EnrichedHoldingData> enrichedHoldings,
        SnapshotMetrics metrics
) {

    public AnalysisSnapshot {
        portfolioRiskFlags = portfolioRiskFlags == null ? List.of() : List.copyOf(portfolioRiskFlags);
        enrichedHoldings = enrichedHoldings == null ? List.of() : List.copyOf(enrichedHoldings);
    }

    public static AnalysisSnapshot from(PortfolioReasoningContext reasoningContext) {
        PortfolioStats portfolioStats = reasoningContext.portfolioStats();
        SnapshotMetrics snapshotMetrics = portfolioStats == null
                ? null
                : new SnapshotMetrics(
                portfolioStats.getLargestWeight(),
                portfolioStats.getDayChange(),
                portfolioStats.getDayChangePercent(),
                portfolioStats.getTop3HoldingPercent(),
                portfolioStats.getDiversificationScore());

        return new AnalysisSnapshot(
                reasoningContext.portfolioUserId(),
                reasoningContext.portfolioSummary(),
                reasoningContext.classification(),
                reasoningContext.portfolioRiskFlags(),
                reasoningContext.enrichedHoldings(),
                snapshotMetrics);
    }

    public PortfolioReasoningContext toReasoningContext() {
        PortfolioSummary summary = portfolioSummary == null
                ? new PortfolioSummary(null, null, null, null, 0)
                : portfolioSummary;

        PortfolioStats portfolioStats = metrics == null
                ? null
                : new PortfolioStats(
                null,
                summary.totalInvested(),
                summary.totalCurrentValue(),
                summary.totalPnL(),
                summary.totalPnLPercent(),
                metrics.largestWeight(),
                summary.totalHoldings(),
                metrics.dayChange(),
                metrics.dayChangePercent(),
                metrics.top3HoldingPercent(),
                metrics.diversificationScore(),
                null);

        return new PortfolioReasoningContext(
                portfolioUserId,
                summary,
                portfolioStats,
                portfolioRiskFlags,
                enrichedHoldings,
                classification);
    }

    public record SnapshotMetrics(
            BigDecimal largestWeight,
            BigDecimal dayChange,
            BigDecimal dayChangePercent,
            BigDecimal top3HoldingPercent,
            BigDecimal diversificationScore
    ) {
    }
}
