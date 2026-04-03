package com.cursor_springa_ai.playground.ai.orchestration;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.dto.PortfolioAnalysisResponse;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.dto.ai.AnalysisSnapshot;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.PortfolioStats;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.model.UserHolding;
import com.cursor_springa_ai.playground.ai.advisor.PortfolioAdvisorAgent;
import com.cursor_springa_ai.playground.ai.persistence.AiAnalysisService;
import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.ai.tools.AnalysisSnapshotBuilder;
import com.cursor_springa_ai.playground.analytics.HoldingAnalyticsService;
import com.cursor_springa_ai.playground.analytics.PortfolioAnalyticsService;
import com.cursor_springa_ai.playground.analytics.PortfolioClassificationService;
import com.cursor_springa_ai.playground.repository.UserHoldingRepository;
import com.cursor_springa_ai.playground.service.ZerodhaAuthService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioAnalysisService {

    private final UserHoldingRepository userHoldingRepository;
    private final PortfolioAdvisorAgent aiPortfolioAdvisorService;
    private final HoldingAnalyticsService holdingAnalyticsService;
    private final PortfolioAnalyticsService portfolioAnalyticsService;
    private final ZerodhaAuthService zerodhaAuthService;
    private final AiAnalysisService aiAnalysisService;
    private final PortfolioClassificationService portfolioClassificationService;
    private final AnalysisSnapshotBuilder snapshotBuilder;

    public PortfolioAnalysisService(
            UserHoldingRepository userHoldingRepository,
            PortfolioAdvisorAgent aiPortfolioAdvisorService,
            HoldingAnalyticsService holdingAnalyticsService,
            PortfolioAnalyticsService portfolioAnalyticsService,
            ZerodhaAuthService zerodhaAuthService,
            AiAnalysisService aiAnalysisService,
            PortfolioClassificationService portfolioClassificationService,
            AnalysisSnapshotBuilder snapshotBuilder) {
        this.userHoldingRepository = userHoldingRepository;
        this.aiPortfolioAdvisorService = aiPortfolioAdvisorService;
        this.holdingAnalyticsService = holdingAnalyticsService;
        this.portfolioAnalyticsService = portfolioAnalyticsService;
        this.zerodhaAuthService = zerodhaAuthService;
        this.aiAnalysisService = aiAnalysisService;
        this.portfolioClassificationService = portfolioClassificationService;
        this.snapshotBuilder = snapshotBuilder;
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

        // Build the snapshot before calling the AI so it reflects the exact context used
        AnalysisSnapshot snapshot = snapshotBuilder.build(reasoningContext);

        PortfolioAdviceResponse aiInsights = aiPortfolioAdvisorService.generateInsights(reasoningContext);

        // Persist the AI response together with the reasoning snapshot (append-only audit log)
        aiAnalysisService.savePortfolioAdvice(currentUser, aiInsights, snapshot);

        return new PortfolioAnalysisResponse(
                portfolioUserId,
                portfolioSummary.totalInvested(),
                portfolioSummary.totalCurrentValue(),
                portfolioSummary.totalPnL(),
                aiInsights);
    }
}
