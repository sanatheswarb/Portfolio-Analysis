package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.dto.PortfolioAnalysisResponse;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.PortfolioStats;
import com.cursor_springa_ai.playground.model.RiskFlag;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.model.UserHolding;
import com.cursor_springa_ai.playground.repository.UserHoldingRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioAnalysisService {

        private final UserHoldingRepository userHoldingRepository;
        private final AiPortfolioAdvisorService aiPortfolioAdvisorService;
        private final EnrichedHoldingDataCache enrichedHoldingDataCache;
        private final ZerodhaAuthService zerodhaAuthService;
        private final AiAnalysisService aiAnalysisService;

        public PortfolioAnalysisService(
                        UserHoldingRepository userHoldingRepository,
                        AiPortfolioAdvisorService aiPortfolioAdvisorService,
                        EnrichedHoldingDataCache enrichedHoldingDataCache,
                        ZerodhaAuthService zerodhaAuthService,
                        AiAnalysisService aiAnalysisService) {
                this.userHoldingRepository = userHoldingRepository;
                this.aiPortfolioAdvisorService = aiPortfolioAdvisorService;
                this.enrichedHoldingDataCache = enrichedHoldingDataCache;
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
                String portfolioUserId = currentUser.getBrokerUserId();

                // PortfolioStats is already fetched via JOIN FETCH on user
                PortfolioStats portfolioStats = !userHoldings.isEmpty()
                                ? userHoldings.getFirst().getUser().getPortfolioStats()
                                : null;
                BigDecimal totalInvested = portfolioStats != null ? portfolioStats.getTotalInvested() : BigDecimal.ZERO;
                BigDecimal totalCurrentValue = portfolioStats != null ? portfolioStats.getTotalValue() : BigDecimal.ZERO;
                BigDecimal totalProfitLoss = portfolioStats != null ? portfolioStats.getTotalPnl() : BigDecimal.ZERO;
                BigDecimal totalPnLPercent = portfolioStats != null && portfolioStats.getPnlPercent() != null
                                ? portfolioStats.getPnlPercent() : BigDecimal.ZERO;
                int stockCount = portfolioStats != null && portfolioStats.getStockCount() != null
                                ? portfolioStats.getStockCount() : userHoldings.size();

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

                PortfolioReasoningContext reasoningContext = new PortfolioReasoningContext(
                                portfolioUserId,
                                portfolioSummary,
                                portfolioStats,
                                calculatePortfolioRiskFlags(portfolioStats, userHoldings),
                                enrichedHoldings);

                PortfolioAdviceResponse aiInsights = aiPortfolioAdvisorService.generateInsights(reasoningContext);

                // Persist the AI response to ai_analysis (append-only audit log)
                aiAnalysisService.savePortfolioAdvice(currentUser, aiInsights);

                return new PortfolioAnalysisResponse(
                                portfolioUserId,
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

        private List<RiskFlag> calculatePortfolioRiskFlags(PortfolioStats stats, List<UserHolding> holdings) {
                if (stats == null || holdings.isEmpty()) {
                        return List.of();
                }
                List<RiskFlag> flags = new ArrayList<>();

                BigDecimal largestWeight = stats.getLargestWeight() != null ? stats.getLargestWeight() : BigDecimal.ZERO;
                BigDecimal top3 = stats.getTop3HoldingPercent() != null ? stats.getTop3HoldingPercent() : BigDecimal.ZERO;
                int stockCount = stats.getStockCount() != null ? stats.getStockCount() : holdings.size();

                if (largestWeight.compareTo(BigDecimal.valueOf(25)) > 0) {
                        flags.add(RiskFlag.HIGH_CONCENTRATION);
                }
                if (top3.compareTo(BigDecimal.valueOf(60)) > 0) {
                        flags.add(RiskFlag.TOP_HEAVY_PORTFOLIO);
                }

                Map<String, BigDecimal> sectorExposure = new HashMap<>();
                for (UserHolding h : holdings) {
                        String sector = h.getInstrument() != null ? h.getInstrument().getSector() : null;
                        if (sector == null || sector.isBlank()) {
                                sector = "UNKNOWN";
                        }
                        BigDecimal w = h.getWeightPercent() != null ? h.getWeightPercent() : BigDecimal.ZERO;
                        sectorExposure.merge(sector, w, BigDecimal::add);
                }
                boolean hasSectorConcentration = sectorExposure.values().stream()
                                .anyMatch(exposure -> exposure.compareTo(BigDecimal.valueOf(40)) > 0);
                if (hasSectorConcentration) {
                        flags.add(RiskFlag.SECTOR_CONCENTRATION);
                }

                if (stockCount < 5) {
                        flags.add(RiskFlag.UNDER_DIVERSIFIED);
                }

                return flags;
        }
}
