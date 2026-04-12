package com.cursor_springa_ai.playground.ai.orchestration;

import com.cursor_springa_ai.playground.ai.advisor.PortfolioAdvisorAgent;
import com.cursor_springa_ai.playground.ai.persistence.AiAnalysisService;
import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.dto.ChatResponse;
import com.cursor_springa_ai.playground.dto.ai.AnalysisSnapshot;
import com.cursor_springa_ai.playground.model.entity.AiAnalysis;
import com.cursor_springa_ai.playground.model.AnalysisType;
import com.cursor_springa_ai.playground.model.entity.User;
import com.cursor_springa_ai.playground.repository.AiAnalysisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PortfolioChatService {

    private final AiAnalysisRepository repo;
    private final PortfolioAdvisorAgent advisor;
    private final AiAnalysisService aiAnalysisService;
    private final ObjectMapper objectMapper;
    private final PortfolioReasoningContextFactory reasoningContextFactory;

    public PortfolioChatService(
            AiAnalysisRepository repo,
            PortfolioAdvisorAgent advisor,
            AiAnalysisService aiAnalysisService,
            ObjectMapper objectMapper,
            PortfolioReasoningContextFactory reasoningContextFactory
    ) {
        this.repo = repo;
        this.advisor = advisor;
        this.aiAnalysisService = aiAnalysisService;
        this.objectMapper = objectMapper;
        this.reasoningContextFactory = reasoningContextFactory;
    }

    public ChatResponse askQuestion(User user, String question) {
        if (question == null || question.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question cannot be empty");
        }

        AiAnalysis analysis = repo.findTopByUserIdAndAnalysisTypeOrderByCreatedAtDesc(
                user.getId(),
                AnalysisType.PORTFOLIO_ANALYSIS
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Run portfolio analysis first"));

        if (analysis.getAnalysisContext() == null || analysis.getAnalysisContext().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Portfolio analysis is incomplete or corrupted");
        }

        AnalysisSnapshot snapshot;
        try {
            snapshot = objectMapper.readValue(analysis.getAnalysisContext(), AnalysisSnapshot.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Failed to read saved portfolio analysis context", e);
        }

        List<AiAnalysis> chats = repo.findTop3ByParentAnalysisIdAndAnalysisTypeOrderByCreatedAtDesc(
                analysis.getId(),
                AnalysisType.PORTFOLIO_CHAT
        );

        PortfolioReasoningContext reasoningContext = reasoningContextFactory.build(user);

        String answer = advisor.answerQuestion(snapshot, reasoningContext, chats, question);
        AiAnalysis chatRow = aiAnalysisService.saveChat(user, question, answer, analysis.getId());

        return new ChatResponse(answer, chatRow.getId(), analysis.getId());
    }
}
