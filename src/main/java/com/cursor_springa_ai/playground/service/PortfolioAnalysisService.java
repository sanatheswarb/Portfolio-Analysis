package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.dto.PortfolioAnalysisResponse;
import com.cursor_springa_ai.playground.dto.PortfolioMetrics;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.PortfolioStats;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.model.UserHolding;
import com.cursor_springa_ai.playground.repository.UserHoldingRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class PortfolioAnalysisService {

        private final UserHoldingRepository userHoldingRepository;
        private final AiPortfolioAdvisorService aiPortfolioAdvisorService;
        private final EnrichedHoldingDataCache enrichedHoldingDataCache;
        private final PortfolioMetricsService portfolioMetricsService;
        private final ZerodhaAuthService zerodhaAuthService;
        private final AiAnalysisService aiAnalysisService;

        public PortfolioAnalysisService(
                        UserHoldingRepository userHoldingRepository,
                        AiPortfolioAdvisorService aiPortfolioAdvisorService,
                        EnrichedHoldingDataCache enrichedHoldingDataCache,
                        PortfolioMetricsService portfolioMetricsService,
                        ZerodhaAuthService zerodhaAuthService,
                        AiAnalysisService aiAnalysisService) {
                this.userHoldingRepository = userHoldingRepository;
                this.aiPortfolioAdvisorService = aiPortfolioAdvisorService;
                this.enrichedHoldingDataCache = enrichedHoldingDataCache;
                this.portfolioMetricsService = portfolioMetricsService;
                this.zerodhaAuthService = zerodhaAuthService;
                this.aiAnalysisService = aiAnalysisService;
        }

        public PortfolioAnalysisResponse analyzeCurrentUserPortfolio() {
                User currentUser = zerodhaAuthService.getCurrentUser();
                if (currentUser == null) {
                        throw new IllegalStateException(
                                        "No authenticated Zerodha user found. Please complete login first.");
                }

                // Single DB query: user_holdings + instrument + stock_fundamentals + portfolio_stats
                List<UserHolding> userHoldings = userHoldingRepository
                                .findByUserIdWithStatsAndFundamentals(currentUser.getId());
                String portfolioId = currentUser.getBrokerUserId();

                long metricsStart = System.currentTimeMillis();

                // PortfolioStats is already fetched via JOIN FETCH on user
                PortfolioStats stats = !userHoldings.isEmpty()
                                ? userHoldings.getFirst().getUser().getPortfolioStats()
                                : null;
                BigDecimal totalInvested = stats != null ? stats.getTotalInvested() : BigDecimal.ZERO;
                BigDecimal totalCurrentValue = stats != null ? stats.getTotalValue() : BigDecimal.ZERO;
                BigDecimal totalProfitLoss = stats != null ? stats.getTotalPnl() : BigDecimal.ZERO;
                BigDecimal totalPnLPercent = stats != null && stats.getPnlPercent() != null
                                ? stats.getPnlPercent() : BigDecimal.ZERO;
                int stockCount = stats != null && stats.getStockCount() != null
                                ? stats.getStockCount() : userHoldings.size();

                // StockFundamentals already loaded via JOIN FETCH on instrument
                // Build enriched holdings entirely from the eagerly-fetched graph
                List<EnrichedHoldingData> enrichedHoldings = enrichedHoldingDataCache
                                .buildEnrichedHoldingsFromDB(userHoldings);

                // Calculate risk flags for each holding
                enrichedHoldings = enrichedHoldingDataCache.calculateRiskFlags(enrichedHoldings);

                // Create portfolio summary
                PortfolioSummary portfolioSummary = new PortfolioSummary(
                                scale(totalInvested),
                                scale(totalCurrentValue),
                                scale(totalProfitLoss),
                                scale(totalPnLPercent),
                                stockCount);

                // Calculate portfolio-level metrics
                PortfolioMetrics portfolioMetrics = portfolioMetricsService.calculatePortfolioMetrics(
                                enrichedHoldings,
                                totalCurrentValue);

                long metricsTime = System.currentTimeMillis() - metricsStart;

                PortfolioReasoningContext reasoningContext = new PortfolioReasoningContext(
                                portfolioId,
                                portfolioSummary,
                                portfolioMetrics,
                                enrichedHoldings);

                PortfolioAdviceResponse aiInsights = aiPortfolioAdvisorService.generateInsights(reasoningContext);

                // Persist the AI response to ai_analysis (append-only audit log)
                aiAnalysisService.savePortfolioAdvice(currentUser, aiInsights);

                java.util.logging.Logger.getLogger(PortfolioAnalysisService.class.getName())
                                .info("Deterministic portfolio metrics time: " + metricsTime + " ms");

                return new PortfolioAnalysisResponse(
                                portfolioId,
                                scale(totalInvested),
                                scale(totalCurrentValue),
                                scale(totalProfitLoss),
                                aiInsights);
        }

        private BigDecimal scale(BigDecimal value) {
                if (value == null) {
                        return BigDecimal.ZERO;
                }
                return value.setScale(2, RoundingMode.HALF_UP);
        }
}
