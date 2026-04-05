package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.AiAnalysis;
import com.cursor_springa_ai.playground.model.RiskFlag;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioChatPromptBuilderTest {

    private final PortfolioChatPromptBuilder builder = new PortfolioChatPromptBuilder(new ObjectMapper());

    @Test
    void buildPrompt_includesSnapshotConversationAndQuestion() {
        AnalysisSnapshot snapshot = AnalysisSnapshot.from(new PortfolioReasoningContext(
                "portfolio-1",
                new PortfolioSummary(BigDecimal.valueOf(10000), BigDecimal.valueOf(11000), BigDecimal.valueOf(1000), BigDecimal.TEN, 1),
                null,
                List.of(RiskFlag.HIGH_CONCENTRATION.name()),
                List.of(new EnrichedHoldingData(
                        "INFY",
                        "equity",
                        BigDecimal.ONE,
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(1100),
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(1100),
                        BigDecimal.valueOf(100),
                        "technology",
                        BigDecimal.valueOf(28),
                        BigDecimal.ONE,
                        BigDecimal.valueOf(25),
                        BigDecimal.valueOf(1200),
                        BigDecimal.valueOf(800),
                        "largecap",
                        BigDecimal.valueOf(950),
                        BigDecimal.valueOf(35),
                        BigDecimal.TEN,
                        BigDecimal.valueOf(-8),
                        "FAIRLY_VALUED",
                        BigDecimal.valueOf(91.67),
                        BigDecimal.valueOf(2),
                        List.of(RiskFlag.HIGH_CONCENTRATION.name()))),
                null));

        AiAnalysis previousChat = new AiAnalysis(
                null,
                AiAnalysisService.TYPE_PORTFOLIO_CHAT,
                "Why is concentration high?",
                "{\"answer\":\"INFY has a high portfolio weight.\"}",
                null,
                1L,
                null,
                null);

        String prompt = builder.buildPrompt(snapshot, List.of(previousChat), "What should I improve next?");

        assertTrue(prompt.contains("PORTFOLIO SNAPSHOT"));
        assertTrue(prompt.contains("Why is concentration high?"));
        assertTrue(prompt.contains("INFY has a high portfolio weight."));
        assertTrue(prompt.contains("What should I improve next?"));
        assertTrue(prompt.contains(RiskFlag.HIGH_CONCENTRATION.name()));
    }
}
