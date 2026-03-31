package com.cursor_springa_ai.playground.model;

import com.cursor_springa_ai.playground.model.enums.ConcentrationLevel;
import com.cursor_springa_ai.playground.model.enums.DiversificationLevel;
import com.cursor_springa_ai.playground.model.enums.PerformanceLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioRiskLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioStyle;

import java.math.BigDecimal;

public record PortfolioClassification(
        PortfolioRiskLevel riskLevel,
        DiversificationLevel diversificationLevel,
        ConcentrationLevel concentrationLevel,
        PerformanceLevel performanceLevel,
        PortfolioStyle portfolioStyle,
        BigDecimal smallCapExposure,
        BigDecimal top3Exposure
) {
}
