package com.cursor_springa_ai.playground.ai.persistence;

import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.model.AiAnalysis;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.repository.AiAnalysisRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.logging.Logger;

/**
 * Persists AI-generated portfolio analysis results to {@code ai_analysis}.
 * Each successful call appends a new row (the table is append-only).
 */
@Service
public class AiAnalysisService {

    static final String TYPE_PORTFOLIO_ADVICE = "PORTFOLIO_ADVICE";

    private static final Logger logger = Logger.getLogger(AiAnalysisService.class.getName());

    private final AiAnalysisRepository aiAnalysisRepository;
    private final ObjectMapper objectMapper;

    public AiAnalysisService(AiAnalysisRepository aiAnalysisRepository) {
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Serialise {@code advice} to JSON and append a row to {@code ai_analysis}.
     *
     * @param user     the authenticated user (may be null — row is stored without FK)
     * @param advice   the AI-generated advice to persist
     */
    @Transactional
    public void savePortfolioAdvice(User user, PortfolioAdviceResponse advice) {
        if (advice == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(advice);
            aiAnalysisRepository.save(new AiAnalysis(user, TYPE_PORTFOLIO_ADVICE, json));
            logger.info("ai_analysis saved: type=" + TYPE_PORTFOLIO_ADVICE
                    + (user != null ? " user=" + user.getId() : " (no user)"));
        } catch (JsonProcessingException e) {
            logger.warning("Failed to serialise PortfolioAdviceResponse for ai_analysis: " + e.getMessage());
        }
    }
}
