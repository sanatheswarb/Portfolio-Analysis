package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.PortfolioStats;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiPortfolioAdvisorServiceTest {

    @Test
        void generateInsights_buildsPromptWithDeterministicPortfolioData() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        PortfolioAdvisorPromptBuilder promptBuilder = mock(PortfolioAdvisorPromptBuilder.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PortfolioReasoningContext reasoningContext = reasoningContext();

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("""
                {"risk_overview":"Risk overview","diversification_feedback":"Diversification feedback","suggestions":["Reduce concentration","Rebalance sectors","Trim weak positions"],"cautionary_note":"Caution"}
                """);
        when(promptBuilder.buildSystemPrompt()).thenReturn("system");
        when(promptBuilder.buildReasoningRequest(eq(reasoningContext), any(String.class), any(String.class)))
                .thenReturn("user");

        AiPortfolioAdvisorService service = new AiPortfolioAdvisorService(builder, objectMapper, promptBuilder);
        PortfolioAdviceResponse response = service.generateInsights(reasoningContext);

        ArgumentCaptor<String> portfolioOverviewCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> flaggedHoldingsCaptor = ArgumentCaptor.forClass(String.class);

        verify(promptBuilder).buildReasoningRequest(
                eq(reasoningContext),
                portfolioOverviewCaptor.capture(),
                flaggedHoldingsCaptor.capture());
        assertEquals("Risk overview", response.riskOverview());
        assertEquals(List.of("Reduce concentration", "Rebalance sectors", "Trim weak positions"), response.suggestions());
        assertTrue(portfolioOverviewCaptor.getValue().contains("\"portfolioUserId\":\"portfolio-1\""));
        assertTrue(portfolioOverviewCaptor.getValue().contains("HIGH_CONCENTRATION"));
        assertTrue(flaggedHoldingsCaptor.getValue().contains("INFY"));
    }

    private PortfolioReasoningContext reasoningContext() {
        PortfolioSummary summary = new PortfolioSummary(
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                1
        );
        PortfolioStats stats = new PortfolioStats(
                1L,
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(35),
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(35),
                BigDecimal.valueOf(45),
                null
        );
        EnrichedHoldingData holding = new EnrichedHoldingData(
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
                List.of("HIGH_CONCENTRATION")
        );

        return new PortfolioReasoningContext(
                "portfolio-1",
                summary,
                stats,
                List.of("HIGH_CONCENTRATION"),
                List.of(holding)
        );
    }
}
