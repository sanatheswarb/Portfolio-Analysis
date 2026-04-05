package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.model.AiAnalysis;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.repository.AiAnalysisRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Persists AI-generated portfolio analysis results to {@code ai_analysis}.
 * Each successful call appends a new row (the table is append-only).
 */
@Service
public class AiAnalysisService {

    static final String TYPE_PORTFOLIO_ANALYSIS = "PORTFOLIO_ANALYSIS";
    static final String TYPE_PORTFOLIO_ADVICE = "PORTFOLIO_ADVICE";
    static final String TYPE_PORTFOLIO_CHAT = "PORTFOLIO_CHAT";
    static final String ANALYSIS_VERSION = "v1";

    private static final Logger logger = Logger.getLogger(AiAnalysisService.class.getName());

    private final AiAnalysisRepository aiAnalysisRepository;
    private final ObjectMapper objectMapper;
    private final String advisorModel;

    public AiAnalysisService(
            AiAnalysisRepository aiAnalysisRepository,
            ObjectMapper objectMapper,
            @Value("${portfolio.advisor.model:qwen2.5:7b-instruct}") String advisorModel
    ) {
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.objectMapper = objectMapper;
        this.advisorModel = advisorModel;
    }

    /**
     * Serialise {@code advice} to JSON and append a row to {@code ai_analysis}.
     *
     * @param user     the authenticated user (may be null — row is stored without FK)
     * @param advice   the AI-generated advice to persist
     */
    @Transactional
    public void savePortfolioAdvice(User user, PortfolioAdviceResponse advice, PortfolioReasoningContext reasoningContext) {
        if (advice == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(advice);
            String contextJson = objectMapper.writeValueAsString(AnalysisSnapshot.from(reasoningContext));
            aiAnalysisRepository.save(new AiAnalysis(
                    user,
                    TYPE_PORTFOLIO_ANALYSIS,
                    null,
                    json,
                    contextJson,
                    null,
                    advisorModel,
                    ANALYSIS_VERSION));
            logger.info("ai_analysis saved: type=" + TYPE_PORTFOLIO_ANALYSIS
                    + (user != null ? " user=" + user.getId() : " (no user)"));
        } catch (JsonProcessingException e) {
            logger.warning("Failed to serialise PortfolioAdviceResponse for ai_analysis: " + e.getMessage());
        }
    }

    @Transactional
    public AiAnalysis saveChat(User user, String question, String answer, Long parentAnalysisId) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("answer", answer));
            return aiAnalysisRepository.save(new AiAnalysis(
                    user,
                    TYPE_PORTFOLIO_CHAT,
                    question,
                    json,
                    null,
                    parentAnalysisId,
                    advisorModel,
                    ANALYSIS_VERSION));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
