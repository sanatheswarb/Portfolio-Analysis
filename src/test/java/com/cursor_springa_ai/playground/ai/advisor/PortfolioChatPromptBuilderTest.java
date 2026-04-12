package com.cursor_springa_ai.playground.ai.advisor;

import com.cursor_springa_ai.playground.dto.ai.AnalysisSnapshot;
import com.cursor_springa_ai.playground.dto.ai.PortfolioStatsSummary;
import com.cursor_springa_ai.playground.model.AiAnalysis;
import com.cursor_springa_ai.playground.model.AnalysisType;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.enums.ConcentrationLevel;
import com.cursor_springa_ai.playground.model.enums.DiversificationLevel;
import com.cursor_springa_ai.playground.model.enums.PerformanceLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioRiskLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioStyle;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioChatPromptBuilderTest {

    private final PortfolioChatPromptBuilder builder = new PortfolioChatPromptBuilder(new ObjectMapper());

    @Test
    void buildPrompt_includesSnapshotQuestionAndNewestFirstChats() {
        String prompt = builder.buildPrompt(
                sampleSnapshot(),
                List.of(
                        new AiAnalysis(null, AnalysisType.PORTFOLIO_CHAT, "Latest question", "{\"answer\":\"Latest answer\"}", null, null, 10L, "model", "V1"),
                        new AiAnalysis(null, AnalysisType.PORTFOLIO_CHAT, "Earlier question", "{\"answer\":\"Earlier answer\"}", null, null, 10L, "model", "V1")
                ),
                "What should I do next?"
        );

        assertTrue(prompt.contains("PORTFOLIO SNAPSHOT"));
        assertTrue(prompt.contains("QUESTION:\nWhat should I do next?"));
        assertTrue(prompt.indexOf("User: Latest question") < prompt.indexOf("User: Earlier question"));
        assertTrue(prompt.contains("AI: Latest answer"));
        assertTrue(prompt.contains("AI: Earlier answer"));
        assertTrue(prompt.contains("HIGH_CONCENTRATION"));
    }

    private AnalysisSnapshot sampleSnapshot() {
        return new AnalysisSnapshot(
                new PortfolioClassification(
                        PortfolioRiskLevel.HIGH,
                        DiversificationLevel.AVERAGE,
                        ConcentrationLevel.CONCENTRATED,
                        PerformanceLevel.GOOD,
                        PortfolioStyle.GROWTH_HEAVY,
                        BigDecimal.valueOf(5),
                        BigDecimal.valueOf(62)
                ),
                new PortfolioStatsSummary(
                        6,
                        BigDecimal.valueOf(35),
                        BigDecimal.valueOf(64),
                        BigDecimal.valueOf(20),
                        BigDecimal.valueOf(28)
                ),
                List.of("HIGH_CONCENTRATION"),
                List.of(),
                List.of()
        );
    }
}
