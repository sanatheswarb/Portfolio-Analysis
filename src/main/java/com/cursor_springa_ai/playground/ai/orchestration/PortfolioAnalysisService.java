package com.cursor_springa_ai.playground.ai.orchestration;

import com.cursor_springa_ai.playground.controller.NotAuthenticatedException;
import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.dto.PortfolioAnalysisResponse;
import com.cursor_springa_ai.playground.dto.ai.AnalysisDecisionTrace;
import com.cursor_springa_ai.playground.dto.ai.AnalysisSnapshot;
import com.cursor_springa_ai.playground.model.entity.User;
import com.cursor_springa_ai.playground.ai.advisor.PortfolioAdvisorAgent;
import com.cursor_springa_ai.playground.ai.persistence.AiAnalysisService;
import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.ai.tools.AnalysisSnapshotBuilder;
import com.cursor_springa_ai.playground.ai.tools.DecisionTraceBuilder;
import com.cursor_springa_ai.playground.service.ZerodhaAuthService;
import org.springframework.stereotype.Service;

@Service
public class PortfolioAnalysisService {

    private final PortfolioAdvisorAgent aiPortfolioAdvisorService;
    private final ZerodhaAuthService zerodhaAuthService;
    private final AiAnalysisService aiAnalysisService;
    private final AnalysisSnapshotBuilder snapshotBuilder;
    private final PortfolioReasoningContextFactory reasoningContextFactory;
    private final DecisionTraceBuilder decisionTraceBuilder;

    public PortfolioAnalysisService(
            PortfolioAdvisorAgent aiPortfolioAdvisorService,
            ZerodhaAuthService zerodhaAuthService,
            AiAnalysisService aiAnalysisService,
            AnalysisSnapshotBuilder snapshotBuilder,
            PortfolioReasoningContextFactory reasoningContextFactory,
            DecisionTraceBuilder decisionTraceBuilder) {
        this.aiPortfolioAdvisorService = aiPortfolioAdvisorService;
        this.zerodhaAuthService = zerodhaAuthService;
        this.aiAnalysisService = aiAnalysisService;
        this.snapshotBuilder = snapshotBuilder;
        this.reasoningContextFactory = reasoningContextFactory;
        this.decisionTraceBuilder = decisionTraceBuilder;
    }

    public PortfolioAnalysisResponse analyzeCurrentUserPortfolio() {
        User currentUser = zerodhaAuthService.getCurrentUser();
        if (currentUser == null) {
            throw new NotAuthenticatedException(
                    "No authenticated Zerodha user found. Please complete login first.");
        }

        // Decision hints are pre-populated by the factory
        PortfolioReasoningContext reasoningContext = reasoningContextFactory.build(currentUser);

        // Build the snapshot and trace before calling the AI so they reflect the exact context used
        AnalysisSnapshot snapshot = snapshotBuilder.build(reasoningContext);
        AnalysisDecisionTrace trace = decisionTraceBuilder.build(reasoningContext);

        PortfolioAdviceResponse aiInsights = aiPortfolioAdvisorService.generateInsights(reasoningContext);

        // Persist the AI response together with the reasoning snapshot and decision trace (append-only audit log)
        aiAnalysisService.savePortfolioAdvice(currentUser, aiInsights, snapshot, trace);

        return new PortfolioAnalysisResponse(
                reasoningContext.portfolioUserId(),
                reasoningContext.portfolioSummary().totalInvested(),
                reasoningContext.portfolioSummary().totalCurrentValue(),
                reasoningContext.portfolioSummary().totalPnL(),
                aiInsights);
    }
}
