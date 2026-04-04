package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.PortfolioStats;
import com.cursor_springa_ai.playground.model.enums.DiversificationLevel;
import com.cursor_springa_ai.playground.model.enums.PerformanceLevel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class PortfolioReasoningTools {

    private static final Logger logger = Logger.getLogger(PortfolioReasoningTools.class.getName());

    private final PortfolioReasoningContext context;
    private final ObjectMapper objectMapper;
    private final List<String> toolInvocationOrder = new ArrayList<>();
    private final Map<String, Integer> toolInvocationCounts = new LinkedHashMap<>();
    private String portfolioOverviewCache;
    private String flaggedHoldingsCache;
    private final Map<String, String> holdingDetailsCache = new LinkedHashMap<>();

    public PortfolioReasoningTools(PortfolioReasoningContext context, ObjectMapper objectMapper) {
        this.context = context;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "portfolio_overview", description = "Returns deterministic portfolio summary, diversification metrics, sector exposure, portfolio risk flags, and the largest holdings. Call this first.")
    public String portfolioOverview() {
        recordToolInvocation("portfolio_overview");
        if (portfolioOverviewCache != null) {
            return portfolioOverviewCache;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("portfolio_identity", portfolioIdentity());
        payload.put("portfolio_structure", portfolioStructure());
        payload.put("portfolio_performance", portfolioPerformance());
        payload.put("portfolio_risk_profile", portfolioRiskProfile());
        payload.put("largest_holdings", largestHoldingsSummary());
        payload.put("portfolio_strengths", portfolioStrengths());
        payload.put("portfolio_concerns", portfolioConcerns());
        portfolioOverviewCache = toJson(payload);
        return portfolioOverviewCache;
    }

    @Tool(name = "flagged_holdings", description = "Returns only holdings that already have deterministic risk flags, sorted by allocation descending. Use this when you need evidence for recommendations.")
    public String flaggedHoldings() {
        recordToolInvocation("flagged_holdings");
        if (flaggedHoldingsCache != null) {
            return flaggedHoldingsCache;
        }
        List<Map<String, Object>> holdings = context.enrichedHoldings().stream()
                .filter(holding -> holding.riskFlags() != null && !holding.riskFlags().isEmpty())
                .sorted((left, right) -> compareByAllocation(right, left))
                .limit(8)
                .map(this::toHoldingSummary)
                .toList();
        flaggedHoldingsCache = toJson(holdings);
        return flaggedHoldingsCache;
    }

    @Tool(name = "holding_details", description = "Returns deterministic metrics and risk flags for a single holding symbol when you need supporting evidence for advice.")
    public String holdingDetails(
            @ToolParam(description = "Stock symbol to inspect, for example INFY or TCS", required = true)
            String symbol
    ) {
        recordToolInvocation("holding_details");
        logger.info("Advisor tool invoked: holding_details symbol=" + symbol);
        if (symbol == null || symbol.isBlank()) {
            return "{\"error\":\"symbol is required\"}";
        }

        String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        if (holdingDetailsCache.containsKey(normalizedSymbol)) {
            return holdingDetailsCache.get(normalizedSymbol);
        }

        String payload = context.enrichedHoldings().stream()
                .filter(holding -> holding.symbol() != null)
                .filter(holding -> holding.symbol().equalsIgnoreCase(normalizedSymbol))
                .findFirst()
                .map(this::toHoldingSummary)
                .map(this::toJson)
                .orElse("{\"error\":\"holding not found for symbol " + normalizedSymbol + "\"}");
        holdingDetailsCache.put(normalizedSymbol, payload);
        return payload;
    }

    public int invocationCount() {
        return toolInvocationOrder.size();
    }

    public Map<String, Integer> invocationCounts() {
        return Map.copyOf(toolInvocationCounts);
    }

    public String firstInvokedTool() {
        return toolInvocationOrder.isEmpty() ? null : toolInvocationOrder.getFirst();
    }

    public boolean hasInvokedTool(String toolName) {
        return toolInvocationCounts.containsKey(toolName);
    }

    private List<Map<String, Object>> largestHoldingsSummary() {
        return context.enrichedHoldings().stream()
                .sorted((left, right) -> compareByAllocation(right, left))
                .limit(3)
                .map(holding -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("symbol", holding.symbol());
                    payload.put("allocation_percent", holding.allocationPercent());
                    return payload;
                })
                .toList();
    }

    private Map<String, Object> toHoldingSummary(EnrichedHoldingData holding) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("symbol", holding.symbol());
        payload.put("sector", holding.sector());
        payload.put("allocationPercent", holding.allocationPercent());
        payload.put("profitPercent", holding.profitPercent());
        payload.put("distanceFromHigh", holding.distanceFromHigh());
        payload.put("marketCapType", holding.marketCapType());
        payload.put("riskFlags", holding.riskFlags());
        return payload;
    }

    private Map<String, Object> portfolioIdentity() {
        Map<String, Object> payload = new LinkedHashMap<>();
        PortfolioClassification classification = context.classification();
        if (classification == null) {
            return payload;
        }

        payload.put("portfolio_style", enumName(classification.portfolioStyle()));
        payload.put("risk_level", enumName(classification.riskLevel()));
        payload.put("diversification_level", enumName(classification.diversificationLevel()));
        payload.put("concentration_level", enumName(classification.concentrationLevel()));
        payload.put("portfolio_health_summary", portfolioHealthSummary(classification));
        return payload;
    }

    private Map<String, Object> portfolioStructure() {
        Map<String, Object> payload = new LinkedHashMap<>();
        PortfolioStats stats = context.portfolioStats();
        PortfolioClassification classification = context.classification();

        payload.put("stock_count", stockCount(stats, context.portfolioSummary()));

        BigDecimal largestHoldingPercent = stats == null ? null : stats.getLargestWeight();
        payload.put("largest_holding_percent", largestHoldingPercent);
        payload.put("largest_holding_assessment", largestHoldingAssessment(largestHoldingPercent));

        BigDecimal top3HoldingsPercent = top3HoldingsPercent(stats, classification);
        payload.put("top3_holdings_percent", top3HoldingsPercent);
        payload.put("top3_holdings_assessment", top3HoldingsAssessment(top3HoldingsPercent));

        payload.put("small_cap_exposure", smallCapExposure(classification));
        payload.put("mid_cap_exposure", marketCapExposure("midcap"));
        payload.put("large_cap_exposure", marketCapExposure("largecap"));

        SectorExposure sectorExposure = topSectorExposure();
        payload.put("top_sector", sectorExposure.sector());
        payload.put("top_sector_percent", sectorExposure.allocationPercent());
        payload.put("sector_concentration_assessment", sectorConcentrationAssessment(sectorExposure.allocationPercent()));
        return payload;
    }

    private Map<String, Object> portfolioPerformance() {
        Map<String, Object> payload = new LinkedHashMap<>();
        PortfolioStats stats = context.portfolioStats();
        PortfolioSummary summary = context.portfolioSummary();
        PortfolioClassification classification = context.classification();

        payload.put("total_invested", stats != null ? stats.getTotalInvested() : summary == null ? null : summary.totalInvested());
        payload.put("current_value", stats != null ? stats.getTotalValue() : summary == null ? null : summary.totalCurrentValue());
        payload.put("total_pnl_percent", stats != null ? stats.getPnlPercent() : summary == null ? null : summary.totalPnLPercent());
        payload.put("daily_change_percent", stats == null ? null : stats.getDayChangePercent());
        payload.put("performance_level", classification == null ? null : enumName(classification.performanceLevel()));
        return payload;
    }

    private Map<String, Object> portfolioRiskProfile() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("risk_level", context.classification() == null ? null : enumName(context.classification().riskLevel()));
        payload.put("risk_flags", context.portfolioRiskFlags());
        return payload;
    }

    private List<String> portfolioStrengths() {
        LinkedHashSet<String> strengths = new LinkedHashSet<>();
        PortfolioClassification classification = context.classification();
        PortfolioStats stats = context.portfolioStats();
        Integer stockCount = stockCount(stats, context.portfolioSummary());
        BigDecimal smallCapExposure = smallCapExposure(classification);
        BigDecimal pnlPercent = stats != null ? stats.getPnlPercent()
                : context.portfolioSummary() == null ? null : context.portfolioSummary().totalPnLPercent();
        BigDecimal topSectorPercent = topSectorExposure().allocationPercent();

        if (classification != null && isPerformanceStrength(classification.performanceLevel())) {
            strengths.add("Overall performance is healthy.");
        } else if (isPositive(pnlPercent)) {
            strengths.add("Portfolio is profitable overall.");
        }

        if (classification != null && DiversificationLevel.GOOD.equals(classification.diversificationLevel())) {
            strengths.add("Diversification is healthy across the portfolio.");
        } else if (stockCount != null && stockCount >= 10) {
            strengths.add("Portfolio spans a healthy number of holdings.");
        }

        if (classification != null && smallCapExposure != null
                && smallCapExposure.compareTo(BigDecimal.valueOf(20)) <= 0) {
            strengths.add("Small-cap exposure is limited.");
        }

        if (topSectorPercent != null && topSectorPercent.compareTo(BigDecimal.valueOf(35)) <= 0) {
            strengths.add("Sector exposure is reasonably balanced.");
        }
        return List.copyOf(strengths);
    }

    private List<String> portfolioConcerns() {
        LinkedHashSet<String> concerns = new LinkedHashSet<>();
        for (String riskFlag : context.portfolioRiskFlags()) {
            concerns.add(concernForRiskFlag(riskFlag));
        }

        SectorExposure sectorExposure = topSectorExposure();
        String sectorConcern = sectorConcentrationConcern(sectorExposure);
        if (sectorConcern != null) {
            concerns.add(sectorConcern);
        }
        return List.copyOf(concerns);
    }

    private String portfolioHealthSummary(PortfolioClassification classification) {
        String riskLevel = enumName(classification.riskLevel());
        String style = enumName(classification.portfolioStyle());
        if (riskLevel == null || style == null) {
            return null;
        }
        return riskLevel + "_RISK_" + style;
    }

    private Integer stockCount(PortfolioStats stats, PortfolioSummary summary) {
        if (stats != null && stats.getStockCount() != null) {
            return stats.getStockCount();
        }
        return summary == null ? null : summary.totalHoldings();
    }

    private BigDecimal top3HoldingsPercent(PortfolioStats stats, PortfolioClassification classification) {
        if (stats != null && stats.getTop3HoldingPercent() != null) {
            return stats.getTop3HoldingPercent();
        }
        return classification == null ? null : classification.top3Exposure();
    }

    private BigDecimal smallCapExposure(PortfolioClassification classification) {
        if (classification != null && classification.smallCapExposure() != null) {
            return classification.smallCapExposure();
        }
        return marketCapExposure("smallcap");
    }

    private BigDecimal marketCapExposure(String marketCapType) {
        return context.enrichedHoldings().stream()
                .filter(holding -> holding.marketCapType() != null)
                .filter(holding -> holding.marketCapType().equalsIgnoreCase(marketCapType))
                .map(EnrichedHoldingData::allocationPercent)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private SectorExposure topSectorExposure() {
        Map<String, BigDecimal> exposureBySector = new LinkedHashMap<>();
        Map<String, String> displaySectorByNormalizedKey = new LinkedHashMap<>();
        for (EnrichedHoldingData holding : context.enrichedHoldings()) {
            if (holding.sector() == null || holding.sector().isBlank() || holding.allocationPercent() == null) {
                continue;
            }
            String normalizedSectorKey = normalizeSectorKey(holding.sector());
            String displaySector = holding.sector().trim();
            displaySectorByNormalizedKey.putIfAbsent(normalizedSectorKey, displaySector);
            exposureBySector.merge(normalizedSectorKey, holding.allocationPercent(), BigDecimal::add);
        }

        return exposureBySector.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> new SectorExposure(displaySectorByNormalizedKey.get(entry.getKey()), entry.getValue()))
                .orElse(new SectorExposure(null, null));
    }

    private String normalizeSectorKey(String sector) {
        return sector.trim().toLowerCase(Locale.ROOT);
    }
    private String largestHoldingAssessment(BigDecimal largestHoldingPercent) {
        if (largestHoldingPercent == null) {
            return null;
        }
        if (largestHoldingPercent.compareTo(BigDecimal.valueOf(25)) >= 0) {
            return "HIGH_CONCENTRATION";
        }
        if (largestHoldingPercent.compareTo(BigDecimal.valueOf(15)) >= 0) {
            return "MODERATE_CONCENTRATION";
        }
        return "BALANCED";
    }

    private String top3HoldingsAssessment(BigDecimal top3HoldingsPercent) {
        if (top3HoldingsPercent == null) {
            return null;
        }
        if (top3HoldingsPercent.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "TOP_HEAVY";
        }
        if (top3HoldingsPercent.compareTo(BigDecimal.valueOf(40)) >= 0) {
            return "MODERATE_TOP_HEAVY";
        }
        return "BALANCED";
    }

    private String sectorConcentrationAssessment(BigDecimal sectorPercent) {
        if (sectorPercent == null) {
            return null;
        }
        if (sectorPercent.compareTo(BigDecimal.valueOf(40)) >= 0) {
            return "SECTOR_CONCENTRATION";
        }
        if (sectorPercent.compareTo(BigDecimal.valueOf(25)) >= 0) {
            return "MODERATE_SECTOR_TILT";
        }
        return "BALANCED";
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isPerformanceStrength(PerformanceLevel performanceLevel) {
        return PerformanceLevel.GOOD.equals(performanceLevel) || PerformanceLevel.STRONG.equals(performanceLevel);
    }

    private String concernForRiskFlag(String riskFlag) {
        if (riskFlag == null) {
            return "Portfolio has an unspecified risk flag.";
        }
        return switch (riskFlag) {
            case "HIGH_CONCENTRATION" -> "Largest holding concentration is high.";
            case "UNDER_DIVERSIFIED" -> "Portfolio diversification is limited.";
            case "TOP_HEAVY_PORTFOLIO" -> "Top holdings dominate total allocation.";
            case "HIGH_VALUATION" -> "Some holdings are trading at elevated valuations.";
            case "DEEP_CORRECTION" -> "Some holdings remain far below recent highs.";
            case "SMALL_CAP_RISK" -> "Small-cap exposure adds volatility risk.";
            case "PROFIT_BOOKING_ZONE" -> "Some holdings may face profit-booking pressure.";
            default -> "Portfolio has risk flag " + riskFlag + ".";
        };
    }

    private String sectorConcentrationConcern(SectorExposure sectorExposure) {
        if (sectorExposure.allocationPercent() == null
                || sectorExposure.allocationPercent().compareTo(BigDecimal.valueOf(40)) < 0
                || sectorExposure.sector() == null) {
            return null;
        }
        return sectorExposure.sector().toLowerCase(Locale.ROOT) + " sector exposure is high at "
                + formatWithoutTrailingZeros(sectorExposure.allocationPercent()) + "%.";
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String formatWithoutTrailingZeros(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private int compareByAllocation(EnrichedHoldingData left, EnrichedHoldingData right) {
        if (left.allocationPercent() == null && right.allocationPercent() == null) {
            return 0;
        }
        if (left.allocationPercent() == null) {
            return -1;
        }
        if (right.allocationPercent() == null) {
            return 1;
        }
        return left.allocationPercent().compareTo(right.allocationPercent());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize reasoning tool payload", exception);
        }
    }

    private void recordToolInvocation(String toolName) {
        String safeToolName = Objects.requireNonNull(toolName, "toolName must not be null");
        toolInvocationOrder.add(safeToolName);
        logger.info("Advisor tool invoked: " + safeToolName);

        Integer currentCount = toolInvocationCounts.get(safeToolName);
        if (currentCount == null) {
            toolInvocationCounts.put(safeToolName, 1);
            return;
        }
        toolInvocationCounts.put(safeToolName, currentCount + 1);
    }

    private record SectorExposure(String sector, BigDecimal allocationPercent) {
    }

}
