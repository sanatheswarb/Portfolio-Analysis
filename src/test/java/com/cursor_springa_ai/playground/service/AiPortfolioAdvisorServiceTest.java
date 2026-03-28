package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.dto.PortfolioMetrics;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiPortfolioAdvisorServiceTest {

    @Test
    void generateInsights_registersReasoningTools() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        PortfolioAdvisorPromptBuilder promptBuilder = mock(PortfolioAdvisorPromptBuilder.class);
        ObjectMapper objectMapper = new ObjectMapper();

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.tools(any(Object[].class))).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("""
                {"risk_overview":"Risk overview","diversification_feedback":"Diversification feedback","suggestions":["Reduce concentration","Rebalance sectors","Trim weak positions"],"cautionary_note":"Caution"}
                """);
        when(promptBuilder.buildSystemPrompt()).thenReturn("system");
        when(promptBuilder.buildReasoningRequest(any())).thenReturn("user");

        AiPortfolioAdvisorService service = new AiPortfolioAdvisorService(builder, objectMapper, promptBuilder);

        PortfolioReasoningContext reasoningContext = new PortfolioReasoningContext(
                "portfolio-1",
                "Alice",
                new PortfolioSummary(BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, 2),
                new PortfolioMetrics(
                        BigDecimal.TEN,
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        2,
                        BigDecimal.ONE,
                        BigDecimal.valueOf(2),
                        Map.of("financial", BigDecimal.valueOf(50)),
                        Map.of("financial", 2),
                        List.of("HIGH_CONCENTRATION"),
                        BigDecimal.valueOf(45)
                ),
                List.of()
        );
        PortfolioAdviceResponse response = service.generateInsights(reasoningContext);

        verify(requestSpec).tools(any(Object[].class));
        assertEquals("Risk overview", response.riskOverview());
        assertEquals(List.of("Reduce concentration", "Rebalance sectors", "Trim weak positions"), response.suggestions());
    }
}
