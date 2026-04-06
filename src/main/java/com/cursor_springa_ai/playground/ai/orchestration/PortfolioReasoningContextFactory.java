package com.cursor_springa_ai.playground.ai.orchestration;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.analytics.HoldingAnalyticsService;
import com.cursor_springa_ai.playground.analytics.PortfolioAnalyticsService;
import com.cursor_springa_ai.playground.analytics.PortfolioClassificationService;
import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.PortfolioStats;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.model.UserHolding;
import com.cursor_springa_ai.playground.repository.UserHoldingRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PortfolioReasoningContextFactory {

    private final UserHoldingRepository userHoldingRepository;
    private final HoldingAnalyticsService holdingAnalyticsService;
    private final PortfolioAnalyticsService portfolioAnalyticsService;
    private final PortfolioClassificationService portfolioClassificationService;

    public PortfolioReasoningContextFactory(
            UserHoldingRepository userHoldingRepository,
            HoldingAnalyticsService holdingAnalyticsService,
            PortfolioAnalyticsService portfolioAnalyticsService,
            PortfolioClassificationService portfolioClassificationService
    ) {
        this.userHoldingRepository = userHoldingRepository;
        this.holdingAnalyticsService = holdingAnalyticsService;
        this.portfolioAnalyticsService = portfolioAnalyticsService;
        this.portfolioClassificationService = portfolioClassificationService;
    }

    public PortfolioReasoningContext build(User user) {
        List<UserHolding> userHoldings = userHoldingRepository.findByUserIdWithStatsAndFundamentals(user.getId());

        PortfolioStats portfolioStats = !userHoldings.isEmpty()
                ? userHoldings.getFirst().getUser().getPortfolioStats()
                : null;
        List<EnrichedHoldingData> enrichedHoldings = holdingAnalyticsService.buildEnrichedHoldings(userHoldings);
        PortfolioSummary portfolioSummary = portfolioAnalyticsService.toPortfolioSummary(portfolioStats);
        PortfolioClassification classification = portfolioClassificationService.classify(portfolioStats, enrichedHoldings);

        return new PortfolioReasoningContext(
                user.getBrokerUserId(),
                portfolioSummary,
                portfolioStats,
                portfolioAnalyticsService.calculatePortfolioRiskFlags(portfolioStats, enrichedHoldings),
                enrichedHoldings,
                classification
        );
    }
}