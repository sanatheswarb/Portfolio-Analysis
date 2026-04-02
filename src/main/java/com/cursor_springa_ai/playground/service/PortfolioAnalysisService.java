package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.dto.PortfolioAnalysisResponse;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.PortfolioStats;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.model.UserHolding;
import com.cursor_springa_ai.playground.repository.UserHoldingRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioAnalysisService {

    private final UserHoldingRepository userHoldingRepository;
    private final AiPortfolioAdvisorService aiPortfolioAdvisorService;
    private final HoldingAnalyticsService holdingAnalyticsService;
    private final PortfolioAnalyticsService portfolioAnalyticsService;
    private final ZerodhaAuthService zerodhaAuthService;
    private final AiAnalysisService aiAnalysisService;
    private final PortfolioClassificationService portfolioClassificationService;

    public PortfolioAnalysisService(
            UserHoldingRepository userHoldingRepository,
            AiPortfolioAdvisorService aiPortfolioAdvisorService,
            HoldingAnalyticsService holdingAnalyticsService,
            PortfolioAnalyticsService portfolioAnalyticsService,
            ZerodhaAuthService zerodhaAuthService,
            AiAnalysisService aiAnalysisService,
            PortfolioClassificationService portfolioClassificationService) {
        this.userHoldingRepository = userHoldingRepository;
        this.aiPortfolioAdvisorService = aiPortfolioAdvisorService;
        this.holdingAnalyticsService = holdingAnalyticsService;
        this.portfolioAnalyticsService = portfolioAnalyticsService;
        this.zerodhaAuthService = zerodhaAuthService;
        this.aiAnalysisService = aiAnalysisService;
        this.portfolioClassificationService = portfolioClassificationService;
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

        PortfolioStats portfolioStats = !userHoldings.isEmpty()
                ? userHoldings.getFirst().getUser().getPortfolioStats()
                : null;
        String portfolioUserId = currentUser.getBrokerUserId();


        List<EnrichedHoldingData> enrichedHoldings = holdingAnalyticsService.buildEnrichedHoldings(userHoldings);

        PortfolioSummary portfolioSummary = portfolioAnalyticsService.toPortfolioSummary(portfolioStats);

        PortfolioClassification classification = portfolioClassificationService.classify(
                portfolioStats,
                enrichedHoldings);

        PortfolioReasoningContext reasoningContext = new PortfolioReasoningContext(
                portfolioUserId,
                portfolioSummary,
                portfolioStats,
                portfolioAnalyticsService.calculatePortfolioRiskFlags(portfolioStats, enrichedHoldings),
                enrichedHoldings,
                classification);

        PortfolioAdviceResponse aiInsights = aiPortfolioAdvisorService.generateInsights(reasoningContext);

        // Persist the AI response to ai_analysis (append-only audit log)
        aiAnalysisService.savePortfolioAdvice(currentUser, aiInsights);

        return new PortfolioAnalysisResponse(
                portfolioUserId,
                portfolioSummary.totalInvested(),
                portfolioSummary.totalCurrentValue(),
                portfolioSummary.totalPnL(),
                aiInsights);
    }
}
