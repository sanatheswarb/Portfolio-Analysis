package com.cursor_springa_ai.playground.ai.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Pre-computed decision signals captured at analysis time for use by the
 * follow-up chat advisor.
 *
 * <p>Signals represent <em>what to do</em> (priority actions) and
 * <em>what is driving risk</em> (risk drivers by flag), making chat answers
 * faster and more deterministic.
 *
 * <p>Stored inside {@link AnalysisSnapshot} in {@code ai_analysis.analysis_context}.
 * Signals must only be computed in {@code AnalysisSnapshotBuilder} — never in
 * tools, prompt builders, or the reasoning context.
 */
public record DecisionSignals(
        /** Highest-priority active risk flag, e.g. {@code HIGH_CONCENTRATION}. */
        String primaryRisk,

        /** Symbol of the holding most responsible for the primary risk. */
        String primaryRiskDriver,

        /** Symbol of the holding with the largest portfolio allocation. */
        String largestHoldingSymbol,

        /** Allocation percentage of the largest holding. */
        BigDecimal largestHoldingPercent,

        /** Ordered list of pre-computed actionable recommendations (up to 3). */
        List<String> priorityActions,

        /** Maps each active risk flag to the holding symbols that triggered it. */
        Map<String, List<String>> riskDriversByFlag
) {
    public DecisionSignals {
        priorityActions = priorityActions == null ? List.of() : List.copyOf(priorityActions);
        riskDriversByFlag = riskDriversByFlag == null ? Map.of() : Map.copyOf(riskDriversByFlag);
    }
}
