package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.HoldingPerformance;
import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.dto.PortfolioAnalysisResponse;
import com.cursor_springa_ai.playground.dto.PortfolioMetrics;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.Holding;
import com.cursor_springa_ai.playground.model.Portfolio;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class PortfolioAnalysisService {

    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(PortfolioAnalysisService.class.getName());
    private final PortfolioService portfolioService;
    private final MarketPriceService marketPriceService;
    private final AiPortfolioAdvisorService aiPortfolioAdvisorService;
    private final EnrichedHoldingDataCache enrichedHoldingDataCache;
    private final PortfolioMetricsService portfolioMetricsService;

    public PortfolioAnalysisService(
            PortfolioService portfolioService,
            MarketPriceService marketPriceService,
            AiPortfolioAdvisorService aiPortfolioAdvisorService,
            EnrichedHoldingDataCache enrichedHoldingDataCache,
            PortfolioMetricsService portfolioMetricsService
    ) {
        this.portfolioService = portfolioService;
        this.marketPriceService = marketPriceService;
        this.aiPortfolioAdvisorService = aiPortfolioAdvisorService;
        this.enrichedHoldingDataCache = enrichedHoldingDataCache;
        this.portfolioMetricsService = portfolioMetricsService;
    }

    public PortfolioAnalysisResponse analyzePortfolio(String portfolioId) {
        Portfolio portfolio = portfolioService.getPortfolio(portfolioId);
        long metricsStart = System.currentTimeMillis();

        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalCurrentValue = BigDecimal.ZERO;
        List<HoldingPerformance> performances = new ArrayList<>();

        for (Holding holding : portfolio.getHoldings().values()) {
            BigDecimal currentPrice = holding.getCurrentPrice() != null ? holding.getCurrentPrice() : marketPriceService.getCurrentPrice(holding.getSymbol());
            BigDecimal invested = holding.getAverageBuyPrice().multiply(holding.getQuantity());
            BigDecimal current = currentPrice.multiply(holding.getQuantity());
            BigDecimal pnl = holding.getProfitLoss() != null ? holding.getProfitLoss() : current.subtract(invested);

            totalInvested = totalInvested.add(invested);
            totalCurrentValue = totalCurrentValue.add(current);

            performances.add(new HoldingPerformance(
                    holding.getSymbol(),
                    holding.getAssetType(),
                    scale(holding.getQuantity()),
                    scale(holding.getAverageBuyPrice()),
                    scale(currentPrice),
                    scale(invested),
                    scale(current),
                    scale(pnl)
            ));
        }

        List<EnrichedHoldingData> enrichedHoldings = enrichedHoldingDataCache.getEnrichedHoldings(portfolioId);

        BigDecimal scaledTotalCurrentValue = scale(totalCurrentValue);
        enrichedHoldings = enrichedHoldingDataCache.calculateWithAllocationPercent(enrichedHoldings, scaledTotalCurrentValue);
        enrichedHoldings = enrichedHoldingDataCache.calculateRiskFlags(enrichedHoldings);

        BigDecimal totalProfitLoss = totalCurrentValue.subtract(totalInvested);
        BigDecimal totalPnLPercent = totalInvested.compareTo(BigDecimal.ZERO) != 0
                ? totalProfitLoss.divide(totalInvested, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        PortfolioSummary portfolioSummary = new PortfolioSummary(
                scale(totalInvested),
                scale(totalCurrentValue),
                scale(totalProfitLoss),
                scale(totalPnLPercent),
                enrichedHoldings.size()
        );

        PortfolioMetrics portfolioMetrics = portfolioMetricsService.calculatePortfolioMetrics(
                enrichedHoldings,
                totalCurrentValue
        );

        long metricsTime = System.currentTimeMillis() - metricsStart;

        PortfolioReasoningContext reasoningContext = new PortfolioReasoningContext(
                portfolio.getId(),
                portfolio.getOwnerName(),
                portfolioSummary,
                portfolioMetrics,
                enrichedHoldings
        );

        PortfolioAdviceResponse aiInsights = aiPortfolioAdvisorService.generateInsights(reasoningContext);

        logger.info("Deterministic portfolio metrics time: " + metricsTime + " ms");

        return new PortfolioAnalysisResponse(
                portfolio.getId(),
                portfolio.getOwnerName(),
                scale(totalInvested),
                scale(totalCurrentValue),
                scale(totalProfitLoss),
                performances,
                aiInsights
        );
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
