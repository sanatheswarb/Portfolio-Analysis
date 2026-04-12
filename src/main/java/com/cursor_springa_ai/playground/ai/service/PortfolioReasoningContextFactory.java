package com.cursor_springa_ai.playground.ai.service;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.ai.tools.DecisionHintsBuilder;
import com.cursor_springa_ai.playground.analytics.HoldingAnalyticsService;
import com.cursor_springa_ai.playground.analytics.PortfolioAnalyticsService;
import com.cursor_springa_ai.playground.analytics.PortfolioClassificationService;
import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.dto.ai.PortfolioDecisionHints;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.entity.PortfolioStats;
import com.cursor_springa_ai.playground.model.entity.User;
import com.cursor_springa_ai.playground.model.entity.UserHolding;
import com.cursor_springa_ai.playground.repository.UserHoldingRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PortfolioReasoningContextFactory {

    private final UserHoldingRepository userHoldingRepository;
    private final HoldingAnalyticsService holdingAnalyticsService;
    private final PortfolioAnalyticsService portfolioAnalyticsService;
    private final PortfolioClassificationService portfolioClassificationService;
    private final DecisionHintsBuilder decisionHintsBuilder;

    public PortfolioReasoningContextFactory(
            UserHoldingRepository userHoldingRepository,
            HoldingAnalyticsService holdingAnalyticsService,
            PortfolioAnalyticsService portfolioAnalyticsService,
            PortfolioClassificationService portfolioClassificationService,
            DecisionHintsBuilder decisionHintsBuilder
    ) {
        this.userHoldingRepository = userHoldingRepository;
        this.holdingAnalyticsService = holdingAnalyticsService;
        this.portfolioAnalyticsService = portfolioAnalyticsService;
        this.portfolioClassificationService = portfolioClassificationService;
        this.decisionHintsBuilder = decisionHintsBuilder;
    }

    public PortfolioReasoningContext build(User user) {
        List<UserHolding> userHoldings = userHoldingRepository.findByUserIdWithStatsAndFundamentals(user.getId());

        PortfolioStats portfolioStats = !userHoldings.isEmpty()
                ? userHoldings.getFirst().getUser().getPortfolioStats()
                : null;
        List<EnrichedHoldingData> enrichedHoldings = holdingAnalyticsService.buildEnrichedHoldings(userHoldings);
        PortfolioSummary portfolioSummary = portfolioAnalyticsService.toPortfolioSummary(portfolioStats);
        PortfolioClassification classification = portfolioClassificationService.classify(portfolioStats, enrichedHoldings);
        List<String> portfolioRiskFlags = portfolioAnalyticsService.calculatePortfolioRiskFlags(portfolioStats, enrichedHoldings);
        PortfolioDecisionHints decisionHints = decisionHintsBuilder.build(
                portfolioStats,
                classification,
                portfolioRiskFlags,
                enrichedHoldings
        );

        return new PortfolioReasoningContext(
                user.getBrokerUserId(),
                portfolioSummary,
                portfolioStats,
                portfolioRiskFlags,
                enrichedHoldings,
                classification,
                decisionHints
        );
    }
}
