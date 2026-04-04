package com.cursor_springa_ai.playground.dto.ai;

import com.cursor_springa_ai.playground.model.PortfolioClassification;

import java.util.List;

/**
 * Lean snapshot of the reasoning context that was used when generating a
 * portfolio analysis.  Stored as {@code analysis_context} JSON in
 * {@code ai_analysis} so that follow-up chat queries can replay the original
 * reasoning without recomputing everything.
 *
 * <p>Keep this under ~5 KB: store only what the AI used to decide, not
 * everything the AI could use.
 */
public record AnalysisSnapshot(
        PortfolioClassification classification,
        PortfolioStatsSummary portfolioStats,
        List<String> riskFlags,
        List<TopHoldingSummary> topHoldings,
        List<SectorExposureSummary> sectorExposure
) {
    public AnalysisSnapshot {
        riskFlags = riskFlags == null ? List.of() : List.copyOf(riskFlags);
        topHoldings = topHoldings == null ? List.of() : List.copyOf(topHoldings);
        sectorExposure = sectorExposure == null ? List.of() : List.copyOf(sectorExposure);
    }
}
