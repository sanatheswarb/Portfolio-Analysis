package com.cursor_springa_ai.playground.ai.persistence;

import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.ai.dto.AnalysisDecisionTrace;
import com.cursor_springa_ai.playground.ai.dto.AnalysisSnapshot;
import com.cursor_springa_ai.playground.model.entity.AiAnalysis;
import com.cursor_springa_ai.playground.model.enums.AnalysisType;
import com.cursor_springa_ai.playground.model.entity.User;
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

    static final String ANALYSIS_VERSION = "V1";

    private static final Logger logger = Logger.getLogger(AiAnalysisService.class.getName());

    private final AiAnalysisRepository aiAnalysisRepository;
    private final ObjectMapper objectMapper;
    private final String advisorModel;

    public AiAnalysisService(AiAnalysisRepository aiAnalysisRepository,
                             @Value("${portfolio.advisor.model:qwen2.5:7b-instruct}") String advisorModel, ObjectMapper objectMapper) {
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.objectMapper = objectMapper;
        this.advisorModel = advisorModel;
    }

    /**
     * Serialise {@code advice}, {@code snapshot} and {@code trace} to JSON and append a row to
     * {@code ai_analysis} with type {@link AnalysisType#PORTFOLIO_ANALYSIS}.
     *
     * @param user     the authenticated user (may be null â€” row is stored without FK)
     * @param advice   the AI-generated advice to persist
     * @param snapshot lean reasoning-context snapshot built from the same context
     *                 that was passed to the AI advisor
     * @param trace    structured decision-input trace capturing the facts that drove the advice
     */
    @Transactional
    public void savePortfolioAdvice(User user, PortfolioAdviceResponse advice,
                                    AnalysisSnapshot snapshot, AnalysisDecisionTrace trace) {
        if (advice == null) {
            return;
        }
        try {
            String adviceJson   = objectMapper.writeValueAsString(advice);
            String snapshotJson = snapshot != null
                    ? objectMapper.writeValueAsString(snapshot)
                    : null;
            String traceJson    = trace != null
                    ? objectMapper.writeValueAsString(trace)
                    : null;
            aiAnalysisRepository.save(new AiAnalysis(
                    user,
                    AnalysisType.PORTFOLIO_ANALYSIS,
                    null,
                    adviceJson,
                    snapshotJson,
                    traceJson,
                    null,
                    advisorModel,
                    ANALYSIS_VERSION));
            logger.info("ai_analysis saved: type=" + AnalysisType.PORTFOLIO_ANALYSIS
                    + " model=" + advisorModel
                    + " version=" + ANALYSIS_VERSION
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
                    AnalysisType.PORTFOLIO_CHAT,
                    question,
                    json,
                    null,
                    null,
                    parentAnalysisId,
                    advisorModel,
                    ANALYSIS_VERSION));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize chat answer for ai_analysis: parentAnalysisId=" + parentAnalysisId
                            + (user != null ? ", userId=" + user.getId() : ", userId=null"),
                    e);
        }
    }
}
