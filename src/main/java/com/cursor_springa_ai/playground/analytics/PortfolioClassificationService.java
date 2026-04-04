package com.cursor_springa_ai.playground.analytics;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.PortfolioStats;
import com.cursor_springa_ai.playground.model.enums.ConcentrationLevel;
import com.cursor_springa_ai.playground.model.enums.DiversificationLevel;
import com.cursor_springa_ai.playground.model.enums.PerformanceLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioRiskLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioStyle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

import static com.cursor_springa_ai.playground.util.BigDecimalUtils.nvl;

@Service
public class PortfolioClassificationService {

    public PortfolioClassification classify(PortfolioStats stats, List<EnrichedHoldingData> holdings) {
        if (stats == null || holdings.isEmpty()) {
            return emptyClassification();
        }

        PortfolioRiskLevel risk = classifyRisk(stats, holdings);
        DiversificationLevel diversification = classifyDiversification(stats);
        ConcentrationLevel concentration = classifyConcentration(stats);
        PerformanceLevel performance = classifyPerformance(stats);
        PortfolioStyle style = classifyStyle(holdings);
        BigDecimal smallCapExposure = calculateSmallCapExposure(holdings);
        BigDecimal top3Exposure = stats.getTop3HoldingPercent();

        return new PortfolioClassification(
                risk,
                diversification,
                concentration,
                performance,
                style,
                smallCapExposure,
                top3Exposure
        );
    }

    private PortfolioRiskLevel classifyRisk(PortfolioStats stats, List<EnrichedHoldingData> holdings) {
        BigDecimal largest = nvl(stats.getLargestWeight());
        BigDecimal top3 = nvl(stats.getTop3HoldingPercent());
        BigDecimal smallCap = calculateSmallCapExposure(holdings);

        if (largest.compareTo(BigDecimal.valueOf(30)) > 0
                || top3.compareTo(BigDecimal.valueOf(70)) > 0
                || smallCap.compareTo(BigDecimal.valueOf(40)) > 0) {
            return PortfolioRiskLevel.HIGH;
        }

        if (largest.compareTo(BigDecimal.valueOf(20)) > 0
                || top3.compareTo(BigDecimal.valueOf(55)) > 0) {
            return PortfolioRiskLevel.MODERATE;
        }

        return PortfolioRiskLevel.LOW;
    }

    private DiversificationLevel classifyDiversification(PortfolioStats stats) {
        int count = stats.getStockCount() != null ? stats.getStockCount() : 0;

        if (count < 5) {
            return DiversificationLevel.POOR;
        }

        if (count < 12) {
            return DiversificationLevel.AVERAGE;
        }

        return DiversificationLevel.GOOD;
    }

    private ConcentrationLevel classifyConcentration(PortfolioStats stats) {
        BigDecimal largest = nvl(stats.getLargestWeight());

        if (largest.compareTo(BigDecimal.valueOf(25)) > 0) {
            return ConcentrationLevel.CONCENTRATED;
        }

        if (largest.compareTo(BigDecimal.valueOf(15)) > 0) {
            return ConcentrationLevel.MODERATE;
        }

        return ConcentrationLevel.BALANCED;
    }

    private PerformanceLevel classifyPerformance(PortfolioStats stats) {
        BigDecimal pnl = nvl(stats.getPnlPercent());

        if (pnl.compareTo(BigDecimal.ZERO) < 0) {
            return PerformanceLevel.WEAK;
        }

        if (pnl.compareTo(BigDecimal.valueOf(8)) < 0) {
            return PerformanceLevel.STABLE;
        }

        if (pnl.compareTo(BigDecimal.valueOf(18)) < 0) {
            return PerformanceLevel.GOOD;
        }

        return PerformanceLevel.STRONG;
    }

    private PortfolioStyle classifyStyle(List<EnrichedHoldingData> holdings) {
        int value = 0;
        int growth = 0;
        int momentum = 0;

        for (EnrichedHoldingData h : holdings) {
            if ("UNDERVALUED".equals(h.valuationFlag())) {
                value++;
            }

            if ("OVERVALUED".equals(h.valuationFlag())) {
                growth++;
            }

            if (h.momentumScore() != null
                    && h.momentumScore().compareTo(BigDecimal.valueOf(75)) > 0) {
                momentum++;
            }
        }

        int total = holdings.size();

        if (momentum > total / 2) {
            return PortfolioStyle.MOMENTUM_HEAVY;
        }

        if (growth > value) {
            return PortfolioStyle.GROWTH_HEAVY;
        }

        if (value > growth) {
            return PortfolioStyle.VALUE_HEAVY;
        }

        return PortfolioStyle.MIXED;
    }

    private BigDecimal calculateSmallCapExposure(List<EnrichedHoldingData> holdings) {
        BigDecimal total = BigDecimal.ZERO;

        for (EnrichedHoldingData h : holdings) {
            if ("SMALL".equalsIgnoreCase(h.marketCapType())) {
                total = total.add(nvl(h.allocationPercent()));
            }
        }

        return total;
    }

    private PortfolioClassification emptyClassification() {
        return new PortfolioClassification(
                PortfolioRiskLevel.LOW,
                DiversificationLevel.POOR,
                ConcentrationLevel.BALANCED,
                PerformanceLevel.STABLE,
                PortfolioStyle.MIXED,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}
