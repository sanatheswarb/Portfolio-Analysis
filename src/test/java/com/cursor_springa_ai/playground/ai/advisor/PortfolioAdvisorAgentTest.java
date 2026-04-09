package com.cursor_springa_ai.playground.ai.advisor;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioChatReasoningTools;
import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningTools;
import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.dto.ai.AnalysisSnapshot;
import com.cursor_springa_ai.playground.dto.ai.PortfolioStatsSummary;
import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.AiAnalysis;
import com.cursor_springa_ai.playground.model.PortfolioStats;
import com.cursor_springa_ai.playground.model.RiskFlag;
import com.cursor_springa_ai.playground.model.AnalysisType;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.enums.ConcentrationLevel;
import com.cursor_springa_ai.playground.model.enums.DiversificationLevel;
import com.cursor_springa_ai.playground.model.enums.PerformanceLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioRiskLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioStyle;
import com.cursor_springa_ai.playground.service.MarketNewsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioAdvisorAgentTest {

    @Test
        void generateInsights_usesToolCallingWithCompactPrompt() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        PortfolioAdvisorPromptBuilder promptBuilder = mock(PortfolioAdvisorPromptBuilder.class);
        PortfolioChatPromptBuilder chatPromptBuilder = mock(PortfolioChatPromptBuilder.class);
        MarketNewsService marketNewsService = mock(MarketNewsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PortfolioReasoningContext reasoningContext = reasoningContext();

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
                when(requestSpec.tools(any(Object[].class))).thenAnswer(invocation -> {
                        assertEquals(1, invocation.getArguments().length);
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

        PortfolioAdvisorAgent service = new PortfolioAdvisorAgent(builder, objectMapper, promptBuilder, chatPromptBuilder, marketNewsService);
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
        PortfolioChatPromptBuilder chatPromptBuilder = mock(PortfolioChatPromptBuilder.class);
        MarketNewsService marketNewsService = mock(MarketNewsService.class);
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

                PortfolioAdvisorAgent service = new PortfolioAdvisorAgent(builder, objectMapper, promptBuilder, chatPromptBuilder, marketNewsService);
                PortfolioAdviceResponse response = service.generateInsights(reasoningContext);

                verify(chatClient, times(2)).prompt();
                assertEquals("Risk overview", response.riskOverview());
                assertEquals("Caution", response.cautionaryNote());
        }

        @Test
        void generateInsights_parsesJsonWrappedInMarkdownFence() {
                ChatClient.Builder builder = mock(ChatClient.Builder.class);
                ChatClient chatClient = mock(ChatClient.class);
                ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
                ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
                PortfolioAdvisorPromptBuilder promptBuilder = mock(PortfolioAdvisorPromptBuilder.class);
                PortfolioChatPromptBuilder chatPromptBuilder = mock(PortfolioChatPromptBuilder.class);
                MarketNewsService marketNewsService = mock(MarketNewsService.class);
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
                                ```json
                                {"risk_overview":"Risk overview","diversification_feedback":"Diversification feedback","suggestions":["Reduce concentration","Rebalance sectors","Trim weak positions"],"cautionary_note":"Caution"}
                                ```
                                """);
                when(promptBuilder.buildSystemPrompt()).thenReturn("system");
                when(promptBuilder.buildReasoningRequest(eq(reasoningContext))).thenReturn("user");

                PortfolioAdvisorAgent service = new PortfolioAdvisorAgent(builder, objectMapper, promptBuilder, chatPromptBuilder, marketNewsService);
                PortfolioAdviceResponse response = service.generateInsights(reasoningContext);

                assertEquals("Risk overview", response.riskOverview());
                assertEquals("Diversification feedback", response.diversificationFeedback());
                assertEquals("Caution", response.cautionaryNote());
        }

        @Test
        void generateInsights_returnsDeterministicFallbackWhenAdvisorReturnsBlankTwice() {
                ChatClient.Builder builder = mock(ChatClient.Builder.class);
                ChatClient chatClient = mock(ChatClient.class);
                ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
                ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
                PortfolioAdvisorPromptBuilder promptBuilder = mock(PortfolioAdvisorPromptBuilder.class);
                PortfolioChatPromptBuilder chatPromptBuilder = mock(PortfolioChatPromptBuilder.class);
                MarketNewsService marketNewsService = mock(MarketNewsService.class);
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
                when(responseSpec.content()).thenReturn("", "");
                when(promptBuilder.buildSystemPrompt()).thenReturn("system");
                when(promptBuilder.buildReasoningRequest(eq(reasoningContext))).thenReturn("user");
                when(promptBuilder.buildRetryReasoningRequest(anyString())).thenReturn("retry-user");

                PortfolioAdvisorAgent service = new PortfolioAdvisorAgent(builder, objectMapper, promptBuilder, chatPromptBuilder, marketNewsService);
                PortfolioAdviceResponse response = service.generateInsights(reasoningContext);

                verify(chatClient, times(2)).prompt();
                assertFalse(response.riskOverview().startsWith("Unable to parse AI response"));
                assertEquals(3, response.suggestions().size());
                assertEquals("AI advisor returned no usable response, so this fallback uses deterministic portfolio metrics only.", response.cautionaryNote());
        }

        @Test
        void generateInsights_failsWhenPortfolioOverviewToolIsNotCalledFirst() {
                ChatClient.Builder builder = mock(ChatClient.Builder.class);
                ChatClient chatClient = mock(ChatClient.class);
                ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
                ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
                PortfolioAdvisorPromptBuilder promptBuilder = mock(PortfolioAdvisorPromptBuilder.class);
                PortfolioChatPromptBuilder chatPromptBuilder = mock(PortfolioChatPromptBuilder.class);
                MarketNewsService marketNewsService = mock(MarketNewsService.class);
                ObjectMapper objectMapper = new ObjectMapper();
                PortfolioReasoningContext reasoningContext = reasoningContext();

                when(builder.build()).thenReturn(chatClient);
                when(chatClient.prompt()).thenReturn(requestSpec);
                when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
                when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
                when(requestSpec.tools(any(Object[].class))).thenAnswer(invocation -> {
                        PortfolioReasoningTools tools = invocation.getArgument(0, PortfolioReasoningTools.class);
                        tools.holdingDetails(List.of("INFY"));
                        return requestSpec;
                });
                when(requestSpec.options(any())).thenReturn(requestSpec);
                when(requestSpec.call()).thenReturn(responseSpec);
                when(responseSpec.content()).thenReturn("""
                                {"risk_overview":"Risk overview","diversification_feedback":"Diversification feedback","suggestions":["Reduce concentration","Rebalance sectors","Trim weak positions"],"cautionary_note":"Caution"}
                                """);
                when(promptBuilder.buildSystemPrompt()).thenReturn("system");
                when(promptBuilder.buildReasoningRequest(eq(reasoningContext))).thenReturn("user");

                PortfolioAdvisorAgent service = new PortfolioAdvisorAgent(builder, objectMapper, promptBuilder, chatPromptBuilder, marketNewsService);

                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                () -> service.generateInsights(reasoningContext));

                assertEquals("Advisor response rejected because no portfolio_overview tool call was made.", exception.getMessage());
        }

        @Test
        void answerQuestion_usesChatPromptBuilderAndReturnsTrimmedContent() {
                ChatClient.Builder builder = mock(ChatClient.Builder.class);
                ChatClient chatClient = mock(ChatClient.class);
                ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
                ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
                PortfolioAdvisorPromptBuilder promptBuilder = mock(PortfolioAdvisorPromptBuilder.class);
                PortfolioChatPromptBuilder chatPromptBuilder = mock(PortfolioChatPromptBuilder.class);
                MarketNewsService marketNewsService = mock(MarketNewsService.class);
                ObjectMapper objectMapper = new ObjectMapper();

                when(builder.build()).thenReturn(chatClient);
                when(chatClient.prompt()).thenReturn(requestSpec);
                when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
                when(requestSpec.tools(any(Object[].class))).thenAnswer(invocation -> {
                        for (Object tools : invocation.getArguments()) {
                                try {
                                        tools.getClass().getMethod("snapshotOverview").invoke(tools);
                                } catch (NoSuchMethodException ignored) {
                                }
                                try {
                                        tools.getClass().getMethod("portfolioOverview").invoke(tools);
                                } catch (NoSuchMethodException ignored) {
                                }
                        }
                        return requestSpec;
                });
                when(requestSpec.options(any())).thenReturn(requestSpec);
                when(requestSpec.call()).thenReturn(responseSpec);
                when(responseSpec.content()).thenReturn("  Snapshot-based answer  ");
                when(chatPromptBuilder.buildPrompt(any(), any(), anyString())).thenReturn("chat-prompt");

                PortfolioAdvisorAgent service = new PortfolioAdvisorAgent(builder, objectMapper, promptBuilder, chatPromptBuilder, marketNewsService);

                String answer = service.answerQuestion(sampleSnapshot(), reasoningContext(), List.of(sampleChat()), "Why is risk high?");

                assertEquals("Snapshot-based answer", answer);
                verify(chatPromptBuilder).buildPrompt(any(), any(), eq("Why is risk high?"));
                verify(requestSpec).user("chat-prompt");
                verify(requestSpec).tools(any(Object[].class));
                verify(requestSpec, never()).system(anyString());
        }

        @Test
        void answerQuestion_rejectsResponseWhenPortfolioToolRunsBeforeSnapshotOverview() {
                ChatClient.Builder builder = mock(ChatClient.Builder.class);
                ChatClient chatClient = mock(ChatClient.class);
                ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
                ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
                PortfolioAdvisorPromptBuilder promptBuilder = mock(PortfolioAdvisorPromptBuilder.class);
                PortfolioChatPromptBuilder chatPromptBuilder = mock(PortfolioChatPromptBuilder.class);
                MarketNewsService marketNewsService = mock(MarketNewsService.class);
                ObjectMapper objectMapper = new ObjectMapper();

                when(builder.build()).thenReturn(chatClient);
                when(chatClient.prompt()).thenReturn(requestSpec);
                when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
                when(requestSpec.tools(any(Object[].class))).thenAnswer(invocation -> {
                        PortfolioChatReasoningTools chatTools = requiredTool(invocation.getArguments(), PortfolioChatReasoningTools.class);
                        PortfolioReasoningTools portfolioTools = requiredTool(invocation.getArguments(), PortfolioReasoningTools.class);
                        portfolioTools.portfolioOverview();
                        chatTools.snapshotOverview();
                        return requestSpec;
                });
                when(requestSpec.options(any())).thenReturn(requestSpec);
                when(requestSpec.call()).thenReturn(responseSpec);
                when(responseSpec.content()).thenReturn("answer");
                when(chatPromptBuilder.buildPrompt(any(), any(), anyString())).thenReturn("chat-prompt");

                PortfolioAdvisorAgent service = new PortfolioAdvisorAgent(builder, objectMapper, promptBuilder, chatPromptBuilder, marketNewsService);

                String answer = service.answerQuestion(sampleSnapshot(), reasoningContext(), List.of(sampleChat()), "Why is risk high?");

                assertEquals("I could not generate a follow-up answer from the saved portfolio analysis.", answer);
        }

        @Test
        void answerQuestion_rejectsResponseWhenAnotherChatToolRunsBeforeSnapshotOverview() {
                ChatClient.Builder builder = mock(ChatClient.Builder.class);
                ChatClient chatClient = mock(ChatClient.class);
                ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
                ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
                PortfolioAdvisorPromptBuilder promptBuilder = mock(PortfolioAdvisorPromptBuilder.class);
                PortfolioChatPromptBuilder chatPromptBuilder = mock(PortfolioChatPromptBuilder.class);
                MarketNewsService marketNewsService = mock(MarketNewsService.class);
                ObjectMapper objectMapper = new ObjectMapper();

                when(builder.build()).thenReturn(chatClient);
                when(chatClient.prompt()).thenReturn(requestSpec);
                when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
                when(requestSpec.tools(any(Object[].class))).thenAnswer(invocation -> {
                        for (Object tools : invocation.getArguments()) {
                                if (tools instanceof PortfolioChatReasoningTools typedChatTools) {
                                        typedChatTools.topHoldings();
                                        typedChatTools.snapshotOverview();
                                }
                        }
                        return requestSpec;
                });
                when(requestSpec.options(any())).thenReturn(requestSpec);
                when(requestSpec.call()).thenReturn(responseSpec);
                when(responseSpec.content()).thenReturn("answer");
                when(chatPromptBuilder.buildPrompt(any(), any(), anyString())).thenReturn("chat-prompt");

                PortfolioAdvisorAgent service = new PortfolioAdvisorAgent(builder, objectMapper, promptBuilder, chatPromptBuilder, marketNewsService);

                String answer = service.answerQuestion(sampleSnapshot(), reasoningContext(), List.of(sampleChat()), "Why is risk high?");

                assertEquals("I could not generate a follow-up answer from the saved portfolio analysis.", answer);
        }

        @Test
        void answerQuestion_rejectsNewsResponseWhenNewsToolIsNotCalled() {
                ChatClient.Builder builder = mock(ChatClient.Builder.class);
                ChatClient chatClient = mock(ChatClient.class);
                ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
                ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
                PortfolioAdvisorPromptBuilder promptBuilder = mock(PortfolioAdvisorPromptBuilder.class);
                PortfolioChatPromptBuilder chatPromptBuilder = mock(PortfolioChatPromptBuilder.class);
                MarketNewsService marketNewsService = mock(MarketNewsService.class);
                ObjectMapper objectMapper = new ObjectMapper();

                when(builder.build()).thenReturn(chatClient);
                when(chatClient.prompt()).thenReturn(requestSpec);
                when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
                when(requestSpec.tools(any(Object[].class))).thenAnswer(invocation -> {
                        PortfolioChatReasoningTools chatTools = requiredTool(invocation.getArguments(), PortfolioChatReasoningTools.class);
                        chatTools.snapshotOverview();
                        return requestSpec;
                });
                when(requestSpec.options(any())).thenReturn(requestSpec);
                when(requestSpec.call()).thenReturn(responseSpec);
                when(responseSpec.content()).thenReturn("ADANIPORTS looks stable.");
                when(chatPromptBuilder.buildPrompt(any(), any(), anyString())).thenReturn("chat-prompt");

                PortfolioAdvisorAgent service = new PortfolioAdvisorAgent(builder, objectMapper, promptBuilder, chatPromptBuilder, marketNewsService);

                String answer = service.answerQuestion(sampleSnapshot(), reasoningContext(), List.of(sampleChat()), "Any recent news on ADANIPORTS?");

                assertEquals("I could not generate a news answer because no recent news tool call was made.", answer);
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
                List.of(RiskFlag.HIGH_CONCENTRATION.name())
        );

        return new PortfolioReasoningContext(
                "portfolio-1",
                summary,
                stats,
                List.of(RiskFlag.HIGH_CONCENTRATION.name()),
                List.of(holding),
                null,
                null
        );
    }

    private AnalysisSnapshot sampleSnapshot() {
        return new AnalysisSnapshot(
                new PortfolioClassification(
                        PortfolioRiskLevel.HIGH,
                        DiversificationLevel.AVERAGE,
                        ConcentrationLevel.CONCENTRATED,
                        PerformanceLevel.GOOD,
                        PortfolioStyle.GROWTH_HEAVY,
                        BigDecimal.valueOf(10),
                        BigDecimal.valueOf(60)
                ),
                new PortfolioStatsSummary(
                        4,
                        BigDecimal.valueOf(30),
                        BigDecimal.valueOf(55),
                        BigDecimal.valueOf(12),
                        BigDecimal.valueOf(25)
                ),
                List.of(RiskFlag.HIGH_CONCENTRATION.name()),
                List.of(),
                List.of()
        );
    }

    private AiAnalysis sampleChat() {
        return new AiAnalysis(
                null,
                AnalysisType.PORTFOLIO_CHAT,
                "How risky is this portfolio?",
                "{\"answer\":\"It is concentrated.\"}",
                null,
                null,
                9L,
                "qwen2.5:7b-instruct",
                "V1"
        );
    }

        private <T> T requiredTool(Object[] tools, Class<T> toolType) {
                for (Object tool : tools) {
                        if (toolType.isInstance(tool)) {
                                return toolType.cast(tool);
                        }
                }
                throw new AssertionError("Missing tool: " + toolType.getSimpleName());
        }
}
