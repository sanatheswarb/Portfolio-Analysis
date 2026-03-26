package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioMetrics;
import org.springframework.stereotype.Service;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import java.util.logging.Logger;

@Service
public class PortfolioMetricsService {

    private static final Logger logger = Logger.getLogger(PortfolioMetricsService.class.getName());

    /**
     * Calculate portfolio-level metrics from enriched holdings.
     */
    @Tool(name = "getPortfolioMetrics", description = "Calculate portfolio-level metrics such as concentration, sector exposure, portfolio risk flags, and diversification score from enriched holdings and total current value.")
    public PortfolioMetrics calculatePortfolioMetrics(
            @ToolParam(description = "List of enriched holdings including allocation percent, sector, and holding risk flags.", required = true)
            List<EnrichedHoldingData> enrichedHoldings,
            @ToolParam(description = "Total current market value of the portfolio.", required = true)
            BigDecimal totalCurrentValue) {
        
        if (enrichedHoldings == null || enrichedHoldings.isEmpty()) {
            logger.warning("Cannot calculate portfolio metrics: no holdings provided");
            return null;
        }

        // 1. Calculate totalInvested (reuse existing field)
        BigDecimal totalInvested = enrichedHoldings.stream()
                .map(EnrichedHoldingData::investedValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Use provided totalCurrentValue (already calculated in analysis)
        BigDecimal scaledTotalCurrentValue = scale(totalCurrentValue);

        // 3. Calculate totalPnL
        BigDecimal totalPnL = scaledTotalCurrentValue.subtract(totalInvested);

        // 4. Calculate totalPnLPercent
        BigDecimal totalPnLPercent = calculatePercentage(totalPnL, totalInvested);

        // 5. Total holdings
        int totalHoldings = enrichedHoldings.size();

        // 6. Sort by allocation percent (descending) to find top holdings
        List<EnrichedHoldingData> sortedByAllocation = enrichedHoldings.stream()
                .sorted(Comparator.comparing(EnrichedHoldingData::allocationPercent, 
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        // 7. Top holding percent
        BigDecimal topHoldingPercent = sortedByAllocation.isEmpty() || sortedByAllocation.get(0).allocationPercent() == null 
                ? BigDecimal.ZERO 
                : sortedByAllocation.get(0).allocationPercent();

        // 8. Top 3 holdings percent
        BigDecimal top3HoldingPercent = sortedByAllocation.stream()
                .limit(3)
                .map(EnrichedHoldingData::allocationPercent)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 9. Sector exposure (groupBy sector, sum allocation percent)
        Map<String, BigDecimal> sectorExposure = enrichedHoldings.stream()
                .collect(Collectors.groupingBy(
                        EnrichedHoldingData::sector,
                        Collectors.mapping(
                                EnrichedHoldingData::allocationPercent,
                                Collectors.collectingAndThen(
                                        Collectors.reducing(BigDecimal.ZERO, BigDecimal::add),
                                        this::scale
                                )
                        )
                ));

        // 10. Sector count (how many holdings per sector)
        Map<String, Integer> sectorCount = enrichedHoldings.stream()
                .collect(Collectors.groupingBy(
                        EnrichedHoldingData::sector,
                        Collectors.collectingAndThen(
                                Collectors.counting(),
                                Long::intValue
                        )
                ));

        // 11. Calculate portfolio risk flags
        List<String> portfolioRiskFlags = calculatePortfolioRiskFlags(
                topHoldingPercent,
                top3HoldingPercent,
                sectorExposure,
                totalHoldings
        );

        // 12. Calculate diversification score (0-100)
        // Using Herfindahl index: sum of squared allocation percentages
        BigDecimal diversificationScore = calculateDiversificationScore(enrichedHoldings);

        PortfolioMetrics metrics = new PortfolioMetrics(
                scale(totalInvested),
                scaledTotalCurrentValue,
                scale(totalPnL),
                totalPnLPercent,
                totalHoldings,
                scale(topHoldingPercent),
                scale(top3HoldingPercent),
                sectorExposure,
                sectorCount,
                portfolioRiskFlags,
                diversificationScore
        );

        logger.info("Portfolio metrics calculated | Holdings: " + totalHoldings + 
                " | P&L: " + totalPnL + " (" + totalPnLPercent + "%) | " +
                "TopHolding: " + topHoldingPercent + "% | Top3: " + top3HoldingPercent + "%");

        return metrics;
    }

    /**
     * Calculate portfolio-level risk flags based on concentration and diversification.
     */
    private List<String> calculatePortfolioRiskFlags(BigDecimal topHoldingPercent,
                                                      BigDecimal top3HoldingPercent,
                                                      Map<String, BigDecimal> sectorExposure,
                                                      int totalHoldings) {
        List<String> riskFlags = new ArrayList<>();

        // 1. Check top holding concentration (> 25%)
        if (topHoldingPercent.compareTo(BigDecimal.valueOf(25)) > 0) {
            riskFlags.add("HIGH_CONCENTRATION");
            logger.info("Portfolio risk: HIGH_CONCENTRATION - Top holding: " + topHoldingPercent + "%");
        }

        // 2. Check top 3 holdings (> 60%)
        if (top3HoldingPercent.compareTo(BigDecimal.valueOf(60)) > 0) {
            riskFlags.add("TOP_HEAVY_PORTFOLIO");
            logger.info("Portfolio risk: TOP_HEAVY_PORTFOLIO - Top 3 holdings: " + top3HoldingPercent + "%");
        }

        // 3. Check sector concentration (any sector > 40%)
        sectorExposure.forEach((sector, exposure) -> {
            if (exposure.compareTo(BigDecimal.valueOf(40)) > 0) {
                String riskFlag = "SECTOR_CONCENTRATION_" + sector.toUpperCase().replace(" ", "_");
                riskFlags.add(riskFlag);
                logger.info("Portfolio risk: " + riskFlag + " - Exposure: " + exposure + "%");
            }
        });

        // 4. Check under-diversification (< 5 holdings)
        if (totalHoldings < 5) {
            riskFlags.add("UNDER_DIVERSIFIED");
            logger.info("Portfolio risk: UNDER_DIVERSIFIED - Total holdings: " + totalHoldings);
        }

        return riskFlags;
    }

    /**
     * Calculate diversification score using Herfindahl-Hirschman Index (HHI).
     * Score ranges from 0-100:
     * - 0: Perfect diversification (equal weight across many holdings)
     * - 100: Single holding (concentration)
     * Uses formula: 100 - sqrt(sum(allocationPercent^2))
     */
    private BigDecimal calculateDiversificationScore(List<EnrichedHoldingData> enrichedHoldings) {
        if (enrichedHoldings == null || enrichedHoldings.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Calculate sum of squared allocations
        BigDecimal sumSquared = enrichedHoldings.stream()
                .map(EnrichedHoldingData::allocationPercent)
                .filter(Objects::nonNull)
                .map(alloc -> alloc.multiply(alloc))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // HHI: sqrt(sum of squared allocations)
        double hhi = Math.sqrt(sumSquared.doubleValue());

        // Diversification score: 100 - HHI
        // Normalized so that:
        // - Single holding (HHI=100) -> Score=0
        // - Equal weight across 10 holdings (HHI≈10) -> Score≈90
        double diversificationScoreValue = 100 - hhi;
        diversificationScoreValue = Math.max(0, Math.min(100, diversificationScoreValue)); // Clamp 0-100

        return BigDecimal.valueOf(diversificationScoreValue).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate percentage: (value / total) * 100
     */
    private BigDecimal calculatePercentage(BigDecimal value, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0 || value == null) {
            return null;
        }
        return value.divide(total, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Scale to 2 decimal places.
     */
    private BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
