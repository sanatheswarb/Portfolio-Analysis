package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.PortfolioStats;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiPortfolioAdvisorServiceTest {

    @Test
        void generateInsights_usesToolCallingWithCompactPrompt() {
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
                when(requestSpec.tools(any(Object[].class))).thenAnswer(invocation -> {
                        PortfolioReasoningTools tools = invocation.getArgument(0, PortfolioReasoningTools.class);
                        tools.portfolioOverview();
                        return requestSpec;
                });
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("""
                {"risk_overview":"Risk overview","diversification_feedback":"Diversification feedback","suggestions":["Reduce concentration","Rebalance sectors","Trim weak positions"],"cautionary_note":"Caution"}
                """);
        when(promptBuilder.buildSystemPrompt()).thenReturn("system");
        when(promptBuilder.buildReasoningRequest(eq(reasoningContext)))
                .thenReturn("user");

        AiPortfolioAdvisorService service = new AiPortfolioAdvisorService(builder, objectMapper, promptBuilder);
        PortfolioAdviceResponse response = service.generateInsights(reasoningContext);

        verify(promptBuilder).buildSystemPrompt();
        verify(promptBuilder).buildReasoningRequest(eq(reasoningContext));
        verify(requestSpec).tools(any(Object[].class));
        assertEquals("Risk overview", response.riskOverview());
        assertEquals(List.of("Reduce concentration", "Rebalance sectors", "Trim weak positions"), response.suggestions());
    }

        @Test
        void generateInsights_retriesOnceWhenInitialJsonResponseIsTruncated() {
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
                when(requestSpec.tools(any(Object[].class))).thenAnswer(invocation -> {
                        PortfolioReasoningTools tools = invocation.getArgument(0, PortfolioReasoningTools.class);
                        tools.portfolioOverview();
                        return requestSpec;
                });
                when(requestSpec.options(any())).thenReturn(requestSpec);
                when(requestSpec.call()).thenReturn(responseSpec);
                when(responseSpec.content()).thenReturn(
                                """
                                {"risk_overview":"Risk overview","diversification_feedback":"Diversification feedback","suggestions":["Reduce concentration","Rebalance sectors","Trim weak positions"],"cautionary_note":"Caut
                                """,
                                """
                                {"risk_overview":"Risk overview","diversification_feedback":"Diversification feedback","suggestions":["Reduce concentration","Rebalance sectors","Trim weak positions"],"cautionary_note":"Caution"}
                                """);
                when(promptBuilder.buildSystemPrompt()).thenReturn("system");
                when(promptBuilder.buildReasoningRequest(eq(reasoningContext))).thenReturn("user");
                when(promptBuilder.buildRetryReasoningRequest(anyString())).thenReturn("retry-user");

                AiPortfolioAdvisorService service = new AiPortfolioAdvisorService(builder, objectMapper, promptBuilder);
                PortfolioAdviceResponse response = service.generateInsights(reasoningContext);

                verify(chatClient, times(2)).prompt();
                assertEquals("Risk overview", response.riskOverview());
                assertEquals("Caution", response.cautionaryNote());
        }

        @Test
        void generateInsights_failsWhenPortfolioOverviewToolIsNotCalledFirst() {
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
                when(requestSpec.tools(any(Object[].class))).thenAnswer(invocation -> {
                        PortfolioReasoningTools tools = invocation.getArgument(0, PortfolioReasoningTools.class);
                        tools.holdingDetails("INFY");
                        return requestSpec;
                });
                when(requestSpec.options(any())).thenReturn(requestSpec);
                when(requestSpec.call()).thenReturn(responseSpec);
                when(responseSpec.content()).thenReturn("""
                                {"risk_overview":"Risk overview","diversification_feedback":"Diversification feedback","suggestions":["Reduce concentration","Rebalance sectors","Trim weak positions"],"cautionary_note":"Caution"}
                                """);
                when(promptBuilder.buildSystemPrompt()).thenReturn("system");
                when(promptBuilder.buildReasoningRequest(eq(reasoningContext))).thenReturn("user");

                AiPortfolioAdvisorService service = new AiPortfolioAdvisorService(builder, objectMapper, promptBuilder);

                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                () -> service.generateInsights(reasoningContext));

                assertEquals("Advisor response rejected because no portfolio_overview tool call was made.", exception.getMessage());
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
                "FAIRLY_VALUED",
                BigDecimal.valueOf(91.67),
                BigDecimal.valueOf(2),
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
