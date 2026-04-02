package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.RiskFlag;
import com.cursor_springa_ai.playground.model.PortfolioStats;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.model.UserHolding;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PortfolioAnalyticsService {

    private static final BigDecimal HIGH_CONCENTRATION_THRESHOLD = BigDecimal.valueOf(25);
    private static final BigDecimal TOP_HEAVY_THRESHOLD = BigDecimal.valueOf(60);
    private static final BigDecimal SECTOR_CONCENTRATION_THRESHOLD = BigDecimal.valueOf(40);
    private static final int MIN_DIVERSIFIED_HOLDINGS = 5;

    public PortfolioStats calculatePortfolioStats(User user, List<UserHolding> holdings, LocalDateTime calculatedAt) {
        List<UserHolding> safeHoldings = holdings == null ? List.of() : holdings;
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal largestWeight = BigDecimal.ZERO;
        BigDecimal dayChange = BigDecimal.ZERO;
        BigDecimal sumWeightSquared = BigDecimal.ZERO;

        for (UserHolding holding : safeHoldings) {
            totalInvested = totalInvested.add(nvl(holding.getInvestedValue()));
            totalValue = totalValue.add(nvl(holding.getCurrentValue()));
            dayChange = dayChange.add(nvl(holding.getDayChange()));
            BigDecimal weight = nvl(holding.getWeightPercent());
            if (weight.compareTo(largestWeight) > 0) {
                largestWeight = weight;
            }
            BigDecimal weightFraction = weight.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            sumWeightSquared = sumWeightSquared.add(weightFraction.multiply(weightFraction));
        }

        BigDecimal totalPnl = totalValue.subtract(totalInvested);
        BigDecimal pnlPercent = totalInvested.compareTo(BigDecimal.ZERO) != 0
                ? totalPnl.divide(totalInvested, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal previousValue = totalValue.subtract(dayChange);
        BigDecimal dayChangePercent = previousValue.compareTo(BigDecimal.ZERO) != 0
                ? dayChange.divide(previousValue, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal top3HoldingPercent = safeHoldings.stream()
                .map(holding -> nvl(holding.getWeightPercent()))
                .sorted(Comparator.reverseOrder())
                .limit(3)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal diversificationScore = calculateDiversificationScore(safeHoldings.size(), sumWeightSquared);

        return new PortfolioStats(
                user.getId(),
                totalInvested,
                totalValue,
                totalPnl,
                pnlPercent,
                largestWeight,
                safeHoldings.size(),
                dayChange,
                dayChangePercent,
                top3HoldingPercent,
                diversificationScore,
                calculatedAt
        );
    }

	public PortfolioSummary toPortfolioSummary(PortfolioStats portfolioStats) {
		if (portfolioStats == null) {
			return new PortfolioSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
		}
		return new PortfolioSummary(
				scale(portfolioStats.getTotalInvested()),
				scale(portfolioStats.getTotalValue()),
				scale(portfolioStats.getTotalPnl()),
				scale(portfolioStats.getPnlPercent()),
				portfolioStats.getStockCount() != null ? portfolioStats.getStockCount() : 0
		);
	}

	public List<String> calculatePortfolioRiskFlags(PortfolioStats stats, List<EnrichedHoldingData> enrichedHoldings) {
		if (stats == null || enrichedHoldings == null || enrichedHoldings.isEmpty()) {
			return List.of();
		}

		List<String> flags = new ArrayList<>();
		BigDecimal largestWeight = nvl(stats.getLargestWeight());
		BigDecimal top3HoldingPercent = nvl(stats.getTop3HoldingPercent());
		int stockCount = stats.getStockCount() != null ? stats.getStockCount() : enrichedHoldings.size();

		if (largestWeight.compareTo(HIGH_CONCENTRATION_THRESHOLD) > 0) {
			flags.add(RiskFlag.HIGH_CONCENTRATION.name());
		}
		if (top3HoldingPercent.compareTo(TOP_HEAVY_THRESHOLD) > 0) {
			flags.add(RiskFlag.TOP_HEAVY_PORTFOLIO.name());
		}

		calculateSectorExposure(enrichedHoldings).forEach((sector, exposure) -> {
			if (exposure.compareTo(SECTOR_CONCENTRATION_THRESHOLD) > 0) {
				flags.add("SECTOR_CONCENTRATION_" + sector.toUpperCase(Locale.ROOT).replace(" ", "_"));
			}
		});

		if (stockCount < MIN_DIVERSIFIED_HOLDINGS) {
			flags.add(RiskFlag.UNDER_DIVERSIFIED.name());
		}

		return List.copyOf(flags);
	}

	private Map<String, BigDecimal> calculateSectorExposure(List<EnrichedHoldingData> enrichedHoldings) {
		Map<String, BigDecimal> sectorExposure = new LinkedHashMap<>();
		for (EnrichedHoldingData holding : enrichedHoldings) {
			String sector = holding.sector();
			if (sector == null || sector.isBlank()) {
				sector = "UNKNOWN";
			}
			sectorExposure.merge(sector, nvl(holding.allocationPercent()), BigDecimal::add);
		}
		return sectorExposure;
	}

	private BigDecimal calculateDiversificationScore(int holdingCount, BigDecimal sumWeightSquared) {
		if (holdingCount <= 1) {
			return BigDecimal.ZERO;
		}

		BigDecimal rawScore = BigDecimal.ONE.subtract(sumWeightSquared);
		BigDecimal maxPossibleScore = BigDecimal.ONE.subtract(
				BigDecimal.ONE.divide(BigDecimal.valueOf(holdingCount), 6, RoundingMode.HALF_UP));

		if (maxPossibleScore.compareTo(BigDecimal.ZERO) <= 0) {
			return BigDecimal.ZERO;
		}

		return rawScore
				.divide(maxPossibleScore, 4, RoundingMode.HALF_UP)
				.max(BigDecimal.ZERO)
				.min(BigDecimal.ONE);
	}

	private BigDecimal scale(BigDecimal value) {
		if (value == null) {
			return BigDecimal.ZERO;
		}
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private BigDecimal nvl(BigDecimal value) {
		return value != null ? value : BigDecimal.ZERO;
	}
}
