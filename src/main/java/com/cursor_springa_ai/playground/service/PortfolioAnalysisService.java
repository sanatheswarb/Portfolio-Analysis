package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.HoldingPerformance;
import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.dto.PortfolioAnalysisResponse;
import com.cursor_springa_ai.playground.dto.PortfolioMetrics;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.Holding;
import com.cursor_springa_ai.playground.model.Portfolio;
import com.cursor_springa_ai.playground.model.User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class PortfolioAnalysisService {

    private final PortfolioService portfolioService;
    private final MarketPriceService marketPriceService;
    private final AiPortfolioAdvisorService aiPortfolioAdvisorService;
    private final EnrichedHoldingDataCache enrichedHoldingDataCache;
    private final PortfolioMetricsService portfolioMetricsService;
    private final ZerodhaAuthService zerodhaAuthService;
    private final AiAnalysisService aiAnalysisService;

    public PortfolioAnalysisService(
            PortfolioService portfolioService,
            MarketPriceService marketPriceService,
            AiPortfolioAdvisorService aiPortfolioAdvisorService,
            EnrichedHoldingDataCache enrichedHoldingDataCache,
            PortfolioMetricsService portfolioMetricsService,
            ZerodhaAuthService zerodhaAuthService,
            AiAnalysisService aiAnalysisService
    ) {
        this.portfolioService = portfolioService;
        this.marketPriceService = marketPriceService;
        this.aiPortfolioAdvisorService = aiPortfolioAdvisorService;
        this.enrichedHoldingDataCache = enrichedHoldingDataCache;
        this.portfolioMetricsService = portfolioMetricsService;
        this.zerodhaAuthService = zerodhaAuthService;
        this.aiAnalysisService = aiAnalysisService;
    }

        public PortfolioAnalysisResponse analyzeCurrentUserPortfolio() {
                User currentUser = zerodhaAuthService.getCurrentUser();
                if (currentUser == null) {
                        throw new IllegalStateException("No authenticated Zerodha user found. Please complete login first.");
                }

                Portfolio portfolio = portfolioService.getPortfolio(currentUser);
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

        // Build enriched holdings on demand from current portfolio data.
        List<EnrichedHoldingData> enrichedHoldings =
                enrichedHoldingDataCache.buildEnrichedHoldings(new ArrayList<>(portfolio.getHoldings().values()));

        // Calculate allocation percent for each holding
        BigDecimal scaledTotalCurrentValue = scale(totalCurrentValue);
        enrichedHoldings = enrichedHoldingDataCache.calculateWithAllocationPercent(enrichedHoldings, scaledTotalCurrentValue);

        // Calculate risk flags for each holding
        enrichedHoldings = enrichedHoldingDataCache.calculateRiskFlags(enrichedHoldings);

        BigDecimal totalProfitLoss = totalCurrentValue.subtract(totalInvested);
        
        // Calculate total P&L percent
        BigDecimal totalPnLPercent = totalInvested.compareTo(BigDecimal.ZERO) != 0 
                ? totalProfitLoss.divide(totalInvested, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) 
                : BigDecimal.ZERO;
        
        // Create portfolio summary
        PortfolioSummary portfolioSummary = new PortfolioSummary(
                scale(totalInvested),
                scale(totalCurrentValue),
                scale(totalProfitLoss),
                scale(totalPnLPercent),
                enrichedHoldings.size()
        );
        
        // Calculate portfolio-level metrics
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

        // Persist the AI response to ai_analysis (append-only audit log)
        aiAnalysisService.savePortfolioAdvice(currentUser, aiInsights);

        java.util.logging.Logger.getLogger(PortfolioAnalysisService.class.getName())
                .info("Deterministic portfolio metrics time: " + metricsTime + " ms");

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
