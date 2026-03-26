package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiPortfolioAdvisorServiceTest {

    @Test
    void generateInsightsWithMetrics_registersPortfolioMetricsTool() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        PortfolioAdvisorPromptBuilder promptBuilder = mock(PortfolioAdvisorPromptBuilder.class);
        PortfolioMetricsService portfolioMetricsService = mock(PortfolioMetricsService.class);

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
        when(promptBuilder.buildPortfolioDataWithMetrics(any(), any(), any())).thenReturn("user");

        AiPortfolioAdvisorService service = new AiPortfolioAdvisorService(builder, promptBuilder, portfolioMetricsService);

        PortfolioAdviceResponse response = service.generateInsightsWithMetrics(
                null,
                List.of(),
                new PortfolioSummary(BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, 0)
        );

        verify(requestSpec).tools(portfolioMetricsService);
        assertEquals("Risk overview", response.riskOverview());
        assertEquals(List.of("Reduce concentration", "Rebalance sectors", "Trim weak positions"), response.suggestions());
    }
}
