package com.cursor_springa_ai.playground.ai.tools;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.ai.AnalysisSnapshot;
import com.cursor_springa_ai.playground.dto.ai.PortfolioStatsSummary;
import com.cursor_springa_ai.playground.dto.ai.SectorExposureSummary;
import com.cursor_springa_ai.playground.dto.ai.TopHoldingSummary;
import com.cursor_springa_ai.playground.model.PortfolioStats;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public AnalysisSnapshot build(PortfolioReasoningContext context) {
        return new AnalysisSnapshot(
                context.classification(),
                extractStats(context),
                context.portfolioRiskFlags(),
                extractTopHoldings(context),
                extractSectorExposure(context)
        );
    }

    private PortfolioStatsSummary extractStats(PortfolioReasoningContext context) {
        PortfolioStats stats = context.portfolioStats();
        PortfolioStatsSummary empty = new PortfolioStatsSummary(0, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        if (stats == null) {
            return empty;
        }

        BigDecimal smallCapExposure = extractSmallCapExposure(context);

        return new PortfolioStatsSummary(
                stats.getStockCount() != null ? stats.getStockCount() : 0,
                stats.getLargestWeight() != null ? stats.getLargestWeight() : BigDecimal.ZERO,
                stats.getTop3HoldingPercent() != null ? stats.getTop3HoldingPercent() : BigDecimal.ZERO,
                stats.getPnlPercent() != null ? stats.getPnlPercent() : BigDecimal.ZERO,
                smallCapExposure
        );
    }

    private BigDecimal extractSmallCapExposure(PortfolioReasoningContext context) {
        if (context.classification() != null
                && context.classification().smallCapExposure() != null) {
            return context.classification().smallCapExposure();
        }
        // Compute from holdings if not in classification
        if (context.enrichedHoldings().isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal smallCapAlloc = context.enrichedHoldings().stream()
                .filter(h -> "smallcap".equalsIgnoreCase(h.marketCapType()))
                .map(h -> h.allocationPercent() != null ? h.allocationPercent() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return smallCapAlloc;
    }

    private List<TopHoldingSummary> extractTopHoldings(PortfolioReasoningContext context) {
        return context.enrichedHoldings().stream()
                .filter(h -> h.allocationPercent() != null)
                .sorted(Comparator.comparing(EnrichedHoldingData::allocationPercent).reversed())
                .limit(TOP_HOLDINGS_LIMIT)
                .map(h -> new TopHoldingSummary(
                        h.symbol(),
                        h.allocationPercent(),
                        h.riskFlags() != null ? h.riskFlags() : List.of()
                ))
                .toList();
    }

    private List<SectorExposureSummary> extractSectorExposure(PortfolioReasoningContext context) {
        Map<String, BigDecimal> sectorTotals = new LinkedHashMap<>();
        for (EnrichedHoldingData h : context.enrichedHoldings()) {
            if (h.sector() == null || h.allocationPercent() == null) {
                continue;
            }
            sectorTotals.merge(h.sector(), h.allocationPercent(), BigDecimal::add);
        }
        List<Map.Entry<String, BigDecimal>> sorted = new ArrayList<>(sectorTotals.entrySet());
        sorted.sort(Map.Entry.<String, BigDecimal>comparingByValue().reversed());
        return sorted.stream()
                .limit(TOP_SECTORS_LIMIT)
                .map(e -> new SectorExposureSummary(e.getKey(), e.getValue()))
                .toList();
    }
}
