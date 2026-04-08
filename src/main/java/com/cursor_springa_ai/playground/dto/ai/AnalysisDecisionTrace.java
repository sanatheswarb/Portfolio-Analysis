package com.cursor_springa_ai.playground.dto.ai;

import java.util.List;

/**
 * Structured reasoning summary of the decision inputs that drove a portfolio analysis.
 *
 * <p>Stores <em>facts that caused advice</em>, not the advice itself.  This makes
 * follow-up chat deterministic: instead of re-running the full AI analysis, the chat
 * advisor can consult the trace to answer "why reduce risk?", "which stock is risky?"
 * or "what changed since last time?".
 *
 * <p>Stored as {@code analysis_trace} JSON in {@code ai_analysis}.
 */
public record AnalysisDecisionTrace(
        /** The highest-priority risk flag that triggered advice, e.g. {@code HIGH_CONCENTRATION}. */
        String primaryRisk,

        /** Symbol of the holding most responsible for the primary risk, e.g. {@code INFY}. */
        String primaryRiskDriver,

        /** Top symbols/factors that contributed to the overall risk assessment. */
        List<String> topRiskDrivers,

        /** Short description of the main diversification problem, e.g. {@code "Top 3 holdings exceed 60%"}. */
        String mainDiversificationIssue,

        /** Short description of the portfolio's main positive attribute, e.g. {@code "Portfolio profitable"}. */
        String mainStrength
) {
    public AnalysisDecisionTrace {
        topRiskDrivers = topRiskDrivers == null ? List.of() : List.copyOf(topRiskDrivers);
    }
}
