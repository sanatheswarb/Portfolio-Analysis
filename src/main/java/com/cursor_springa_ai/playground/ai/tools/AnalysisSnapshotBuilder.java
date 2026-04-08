package com.cursor_springa_ai.playground.ai.tools;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.dto.ai.AnalysisSnapshot;
import com.cursor_springa_ai.playground.dto.ai.PortfolioStatsSummary;
import com.cursor_springa_ai.playground.dto.ai.SectorExposureSummary;
import com.cursor_springa_ai.playground.dto.ai.TopHoldingSummary;
import com.cursor_springa_ai.playground.model.PortfolioStats;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Converts a {@link PortfolioReasoningContext} into a lean {@link AnalysisSnapshot}
 * suitable for storage in {@code ai_analysis.analysis_context}.
 *
 * <p>Only the fields that actually drove the AI decision are captured — full
 * holding details, fundamentals, and momentum data are intentionally excluded
 * to keep the stored snapshot under ~5 KB.
 */
@Component
public class AnalysisSnapshotBuilder {

    /** Number of top holdings to include in the snapshot. */
    private static final int TOP_HOLDINGS_LIMIT = 5;

    /** Number of top sectors to include in the snapshot. */
    private static final int TOP_SECTORS_LIMIT = 5;
    private final PortfolioDerivedMetricsService derivedMetricsService;
    private final DecisionHintsBuilder decisionHintsBuilder;

    public AnalysisSnapshotBuilder(
            PortfolioDerivedMetricsService derivedMetricsService,
            DecisionHintsBuilder decisionHintsBuilder
    ) {
        this.derivedMetricsService = derivedMetricsService;
        this.decisionHintsBuilder = decisionHintsBuilder;
    }

    public AnalysisSnapshot build(PortfolioReasoningContext context) {
        return new AnalysisSnapshot(
                context.classification(),
                extractStats(context),
                context.portfolioRiskFlags(),
                extractTopHoldings(context),
                extractSectorExposure(context),
                context.decisionHints() != null ? context.decisionHints() : decisionHintsBuilder.build(context)
        );
    }

    private PortfolioStatsSummary extractStats(PortfolioReasoningContext context) {
        PortfolioStats stats = context.portfolioStats();
        PortfolioStatsSummary empty = new PortfolioStatsSummary(0, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        if (stats == null) {
            return empty;
        }

        BigDecimal smallCapExposure = derivedMetricsService.smallCapExposure(context);

        return new PortfolioStatsSummary(
                stats.getStockCount() != null ? stats.getStockCount() : 0,
                stats.getLargestWeight() != null ? stats.getLargestWeight() : BigDecimal.ZERO,
                stats.getTop3HoldingPercent() != null ? stats.getTop3HoldingPercent() : BigDecimal.ZERO,
                stats.getPnlPercent() != null ? stats.getPnlPercent() : BigDecimal.ZERO,
                smallCapExposure
        );
    }

    private List<TopHoldingSummary> extractTopHoldings(PortfolioReasoningContext context) {
        return derivedMetricsService.topHoldings(context, TOP_HOLDINGS_LIMIT).stream()
                .map(h -> new TopHoldingSummary(
                        h.symbol(),
                        h.allocationPercent(),
                        h.riskFlags() != null ? h.riskFlags() : List.of()
                ))
                .toList();
    }

    private List<SectorExposureSummary> extractSectorExposure(PortfolioReasoningContext context) {
        return derivedMetricsService.sectorExposure(context, TOP_SECTORS_LIMIT);
    }
}
