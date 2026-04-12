package com.cursor_springa_ai.playground.ai.tools;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.analytics.model.EnrichedHoldingData;
import com.cursor_springa_ai.playground.ai.dto.AnalysisSnapshot;
import com.cursor_springa_ai.playground.ai.dto.DecisionSignals;
import com.cursor_springa_ai.playground.ai.dto.PortfolioStatsSummary;
import com.cursor_springa_ai.playground.ai.dto.SectorExposureSummary;
import com.cursor_springa_ai.playground.ai.dto.TopHoldingSummary;
import com.cursor_springa_ai.playground.analytics.PortfolioDerivedMetricsService;
import com.cursor_springa_ai.playground.model.entity.PortfolioStats;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Converts a {@link PortfolioReasoningContext} into a lean {@link AnalysisSnapshot}
 * suitable for storage in {@code ai_analysis.analysis_context}.
 *
 * <p>Only the fields that actually drove the AI decision are captured — full
 * holding details, fundamentals, and momentum data are intentionally excluded
 * to keep the stored snapshot under ~5 KB.
 *
 * <p>{@link DecisionSignals} are computed here and only here — never in tools,
 * prompt builders, or the reasoning context.
 */
@Component
public class AnalysisSnapshotBuilder {

    /** Number of top holdings to include in the snapshot. */
    private static final int TOP_HOLDINGS_LIMIT = 5;

    /** Number of top sectors to include in the snapshot. */
    private static final int TOP_SECTORS_LIMIT = 5;

    /** Maximum number of priority actions to include in decision signals. */
    private static final int MAX_PRIORITY_ACTIONS = 3;

    /**
     * Safe single-holding concentration threshold used in priority action messages.
     * Aligns with {@code HoldingAnalyticsService.HOLDING_CONCENTRATION_THRESHOLD} (20 %).
     */
    private static final int SAFE_HOLDING_CONCENTRATION_PERCENT = 20;

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
                decisionHintsBuilder.resolve(context),
                buildDecisionSignals(context)
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

    /**
     * Builds pre-computed {@link DecisionSignals} from the reasoning context.
     *
     * <p>Signals are computed exclusively here in {@code AnalysisSnapshotBuilder} so
     * that tools, prompt builders, and the reasoning context remain free of signal logic.
     */
    private DecisionSignals buildDecisionSignals(PortfolioReasoningContext context) {
        EnrichedHoldingData largestHolding = derivedMetricsService.topHoldings(context, 1)
                .stream().findFirst().orElse(null);

        String largestHoldingSymbol = largestHolding != null ? largestHolding.symbol() : null;
        BigDecimal largestHoldingPercent = context.portfolioStats() != null
                ? context.portfolioStats().getLargestWeight()
                : (largestHolding != null ? largestHolding.allocationPercent() : null);

        Map<String, List<String>> riskDriversByFlag = buildRiskDriversByFlag(context);
        String primaryRisk = RiskFlagPrioritizer.primaryRisk(context.portfolioRiskFlags());
        String primaryRiskDriver = primaryRiskDriverSymbol(primaryRisk, largestHoldingSymbol, riskDriversByFlag);
        List<String> priorityActions = buildPriorityActions(context, largestHolding, largestHoldingPercent);

        return new DecisionSignals(
                primaryRisk,
                primaryRiskDriver,
                largestHoldingSymbol,
                largestHoldingPercent,
                priorityActions,
                riskDriversByFlag
        );
    }

    /**
     * Returns the symbol of the holding most responsible for the primary risk.
     * Looks up from the risk-drivers map first; falls back to the largest holding.
     */
    private String primaryRiskDriverSymbol(String primaryRisk,
                                           String largestHoldingSymbol,
                                           Map<String, List<String>> riskDriversByFlag) {
        if (primaryRisk == null) {
            return null;
        }
        List<String> drivers = riskDriversByFlag.get(primaryRisk);
        if (drivers != null && !drivers.isEmpty()) {
            return drivers.getFirst();
        }
        return largestHoldingSymbol;
    }

    /**
     * Maps each holding-level risk flag to the list of symbols that triggered it,
     * in descending allocation order.
     */
    private Map<String, List<String>> buildRiskDriversByFlag(PortfolioReasoningContext context) {
        Map<String, List<String>> driversMap = new LinkedHashMap<>();
        // Iterate holdings sorted by allocation descending so the first driver is the largest
        derivedMetricsService.topHoldings(context, context.enrichedHoldings().size()).forEach(holding -> {
            if (holding.riskFlags() == null || holding.riskFlags().isEmpty()) {
                return;
            }
            for (String flag : holding.riskFlags()) {
                driversMap.computeIfAbsent(flag, k -> new ArrayList<>()).add(holding.symbol());
            }
        });
        // Wrap each list as unmodifiable
        Map<String, List<String>> result = new LinkedHashMap<>();
        driversMap.forEach((flag, symbols) -> result.put(flag, List.copyOf(symbols)));
        return result;
    }

    /**
     * Generates up to {@value #MAX_PRIORITY_ACTIONS} pre-computed actionable recommendations
     * from the active portfolio risk flags, in priority order.
     */
    private List<String> buildPriorityActions(PortfolioReasoningContext context,
                                              EnrichedHoldingData largestHolding,
                                              BigDecimal largestHoldingPercent) {
        List<String> sortedFlags = RiskFlagPrioritizer.sortByPriority(context.portfolioRiskFlags());

        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String flag : sortedFlags) {
            if (actions.size() >= MAX_PRIORITY_ACTIONS) {
                break;
            }
            String action = actionForFlag(flag, largestHolding, largestHoldingPercent);
            if (action != null) {
                actions.add(action);
            }
        }
        return List.copyOf(actions);
    }

    private String actionForFlag(String flag, EnrichedHoldingData largestHolding,
                                 BigDecimal largestHoldingPercent) {
        if (flag == null) {
            return null;
        }
        return switch (flag) {
            case "HIGH_CONCENTRATION" -> largestHolding != null
                    ? "Reduce " + largestHolding.symbol() + " allocation from "
                      + formatPercent(largestHoldingPercent) + " to below " + SAFE_HOLDING_CONCENTRATION_PERCENT + "%"
                    : "Reduce largest holding allocation to below " + SAFE_HOLDING_CONCENTRATION_PERCENT + "%";
            case "UNDER_DIVERSIFIED"   -> "Add more holdings to improve portfolio diversification";
            case "TOP_HEAVY_PORTFOLIO" -> "Rebalance top holdings to reduce concentration risk";
            case "HIGH_VALUATION"      -> "Review overvalued holdings for potential rebalancing";
            case "DEEP_CORRECTION"     -> "Review holdings that are far below their 52-week highs";
            case "SMALL_CAP_RISK"      -> "Reduce small-cap exposure to manage volatility";
            case "PROFIT_BOOKING_ZONE" -> "Consider booking profits on high-gain positions";
            default                    -> null;
        };
    }

    private String formatPercent(BigDecimal value) {
        return value == null ? "current level" : value.stripTrailingZeros().toPlainString() + "%";
    }

}
