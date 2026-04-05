package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.ChatResponse;
import com.cursor_springa_ai.playground.model.AiAnalysis;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.repository.AiAnalysisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PortfolioChatService {

    private final AiAnalysisRepository repo;
    private final AiPortfolioAdvisorService advisor;
    private final AiAnalysisService aiAnalysisService;
    private final ObjectMapper objectMapper;

    public PortfolioChatService(
            AiAnalysisRepository repo,
            AiPortfolioAdvisorService advisor,
            AiAnalysisService aiAnalysisService,
            ObjectMapper objectMapper
    ) {
        this.repo = repo;
        this.advisor = advisor;
        this.aiAnalysisService = aiAnalysisService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ChatResponse askQuestion(User user, String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question cannot be empty");
        }

        AiAnalysis analysis = repo.findTopByUserIdAndAnalysisTypeOrderByCreatedAtDesc(
                        user.getId(),
                        AiAnalysisService.TYPE_PORTFOLIO_ANALYSIS)
                .or(() -> repo.findTopByUserIdAndAnalysisTypeOrderByCreatedAtDesc(
                        user.getId(),
                        AiAnalysisService.TYPE_PORTFOLIO_ADVICE))
                .orElseThrow(() -> new IllegalStateException("Run portfolio analysis first"));

        if (analysis.getAnalysisContext() == null || analysis.getAnalysisContext().isBlank()) {
            throw new IllegalStateException("Run portfolio analysis first");
        }

        AnalysisSnapshot snapshot;
        try {
            snapshot = objectMapper.readValue(analysis.getAnalysisContext(), AnalysisSnapshot.class);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }

        List<AiAnalysis> chats = repo.findTop3ByParentAnalysisIdAndAnalysisTypeOrderByCreatedAtDesc(
                analysis.getId(),
                AiAnalysisService.TYPE_PORTFOLIO_CHAT);

        String answer = advisor.answerQuestion(snapshot, chats, question);

        AiAnalysis chatRow = aiAnalysisService.saveChat(user, question, answer, analysis.getId());

        return new ChatResponse(answer, chatRow.getId(), analysis.getId());
    }
}
