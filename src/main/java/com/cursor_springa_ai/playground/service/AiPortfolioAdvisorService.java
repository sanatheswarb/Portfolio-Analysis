package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.model.AiAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.logging.Logger;

@Service
public class AiPortfolioAdvisorService {

        private static final Logger logger = Logger.getLogger(AiPortfolioAdvisorService.class.getName());
        private final ChatClient chatClient;
        private final ObjectMapper objectMapper;
        private final PortfolioAdvisorPromptBuilder promptBuilder;
        private final PortfolioChatPromptBuilder chatPromptBuilder;

        @Value("${portfolio.advisor.model:qwen2.5:7b-instruct}")
        private String advisorModel;

        @Value("${portfolio.advisor.temperature:0.2}")
        private double temperature;

        @Value("${portfolio.advisor.num-predict:192}")
        private int numPredict;

        @Value("${portfolio.advisor.keep-alive:10m}")
        private String keepAlive;

        @Value("${portfolio.advisor.internal-tool-execution:true}")
        private boolean internalToolExecutionEnabled;

        @Value("${portfolio.advisor.thinking-enabled:false}")
        private boolean thinkingEnabled;

        public AiPortfolioAdvisorService(ChatClient.Builder chatClientBuilder,
                        ObjectMapper objectMapper,
                        PortfolioAdvisorPromptBuilder promptBuilder,
                        PortfolioChatPromptBuilder chatPromptBuilder) {
                this.chatClient = chatClientBuilder.build();
                this.objectMapper = objectMapper;
                this.promptBuilder = promptBuilder;
                this.chatPromptBuilder = chatPromptBuilder;
        }

        public PortfolioAdviceResponse generateInsights(PortfolioReasoningContext reasoningContext) {
                String systemPrompt = promptBuilder.buildSystemPrompt();
                String userPrompt = promptBuilder.buildReasoningRequest(reasoningContext);

                AdvisorCallResult firstAttempt = callAdvisor(reasoningContext, systemPrompt, userPrompt, numPredict, temperature);
                PortfolioAdviceResponse parsedFirstAttempt = parseAdviceResponse(firstAttempt.aiResponse(), true);
                if (parsedFirstAttempt != null) {
                        return parsedFirstAttempt;
                }

                int retryNumPredict = Math.max(numPredict + 64, 192);
                double retryTemperature = Math.min(temperature, 0.1d);
                String retryUserPrompt = promptBuilder.buildRetryReasoningRequest(userPrompt);
                logger.info("Retrying advisor response with higher token budget after truncated JSON response");

                AdvisorCallResult secondAttempt = callAdvisor(
                                reasoningContext,
                                systemPrompt,
                                retryUserPrompt,
                                retryNumPredict,
                                retryTemperature);
                PortfolioAdviceResponse parsedSecondAttempt = parseAdviceResponse(secondAttempt.aiResponse(), false);
                if (parsedSecondAttempt != null) {
                        return parsedSecondAttempt;
                }

                return fallbackAdviceResponse(secondAttempt.aiResponse());
        }

        public String answerQuestion(AnalysisSnapshot snapshot, List<AiAnalysis> chats, String question) {
                String systemPrompt = promptBuilder.buildSystemPrompt();
                String userPrompt = chatPromptBuilder.buildPrompt(snapshot, chats, question);
                PortfolioReasoningContext reasoningContext = snapshot.toReasoningContext();
                PortfolioReasoningTools reasoningTools = new PortfolioReasoningTools(reasoningContext, objectMapper);

                String response = chatClient.prompt()
                                .system(systemPrompt)
                                .user(userPrompt)
                                .tools(reasoningTools)
                                .options(buildOptions(Math.max(numPredict, 256), Math.min(temperature, 0.2d)))
                                .call()
                                .content();

                if (response == null || response.isBlank()) {
                        return "I could not generate an answer from the saved portfolio analysis.";
                }
                return response.trim();
        }

        private AdvisorCallResult callAdvisor(
                        PortfolioReasoningContext reasoningContext,
                        String systemPrompt,
                        String userPrompt,
                        int responseNumPredict,
                        double responseTemperature) {
                PortfolioReasoningTools reasoningTools = new PortfolioReasoningTools(reasoningContext, objectMapper);
                OllamaChatOptions options = buildOptions(responseNumPredict, responseTemperature);

                long startTime = System.currentTimeMillis();
                String aiResponse = chatClient.prompt()
                                .system(systemPrompt)
                                .user(userPrompt)
                                .tools(reasoningTools)
                                .options(options)
                                .call()
                                .content();
                long llmTime = System.currentTimeMillis() - startTime;
                logger.info("LLM time: " + llmTime + " ms");
                validateRequiredToolUsage(reasoningTools);
                logger.info("Advisor tool usage summary: firstTool=" + reasoningTools.firstInvokedTool()
                                + ", totalCalls=" + reasoningTools.invocationCount()
                                + ", counts=" + reasoningTools.invocationCounts());
                logRepeatedOverviewUsage(reasoningTools);
                logMissingDrillDownUsage(reasoningTools);
                return new AdvisorCallResult(aiResponse, llmTime);
        }

        private OllamaChatOptions buildOptions(int responseNumPredict, double responseTemperature) {
                OllamaChatOptions.Builder optionsBuilder = OllamaChatOptions.builder()
                                .model(advisorModel)
                                .temperature(responseTemperature)
                                .numPredict(responseNumPredict)
                                .keepAlive(keepAlive)
                                .topP(0.7)
                                .internalToolExecutionEnabled(internalToolExecutionEnabled);

                if (thinkingEnabled) {
                        optionsBuilder.enableThinking();
                } else {
                        optionsBuilder.disableThinking();
                }

                return optionsBuilder.build();
        }

        private void validateRequiredToolUsage(PortfolioReasoningTools reasoningTools) {
                if (!reasoningTools.hasInvokedTool("portfolio_overview")) {
                        throw new IllegalStateException(
                                        "Advisor response rejected because no portfolio_overview tool call was made.");
                }
                if (!"portfolio_overview".equals(reasoningTools.firstInvokedTool())) {
                        throw new IllegalStateException(
                                        "Advisor response rejected because the first tool call was not portfolio_overview.");
                }
        }

        private void logMissingDrillDownUsage(PortfolioReasoningTools reasoningTools) {
                if (reasoningTools.hasInvokedTool("flagged_holdings") || reasoningTools.hasInvokedTool("holding_details")) {
                        return;
                }
                logger.info("Advisor completed without drill-down tool calls; only portfolio_overview data was used.");
        }

        private void logRepeatedOverviewUsage(PortfolioReasoningTools reasoningTools) {
                Integer overviewCalls = reasoningTools.invocationCounts().get("portfolio_overview");
                if (overviewCalls == null || overviewCalls <= 1) {
                        return;
                }
                logger.info("Advisor called portfolio_overview multiple times in one request: count=" + overviewCalls);
        }

        private PortfolioAdviceResponse parseAdviceResponse(String aiResponse, boolean retryOnTruncation) {
                if (aiResponse == null || aiResponse.isBlank()) {
                        return null;
                }
                try {
                        PortfolioAdviceResponse parsed = objectMapper.readValue(aiResponse, PortfolioAdviceResponse.class);
                        return normalizeAdviceResponse(parsed);
                } catch (Exception e) {
                        if (retryOnTruncation && isLikelyTruncatedResponse(aiResponse, e)) {
                                return null;
                        }
                        logger.warning("Failed to parse AI response as JSON: " + e.getMessage());
                        logger.warning("Raw AI response: " + aiResponse);

                        PortfolioAdviceResponse fallback = tryExtractSuggestionsFromMalformedJson(aiResponse);
                        if (fallback != null) {
                                return normalizeAdviceResponse(fallback);
                        }
                        return fallbackAdviceResponse(aiResponse);
                }
        }

        private boolean isLikelyTruncatedResponse(String aiResponse, Exception exception) {
                String message = exception.getMessage();
                if (message != null && message.contains("Unexpected end-of-input")) {
                        return true;
                }
                String trimmed = aiResponse == null ? "" : aiResponse.trim();
                return !trimmed.isEmpty() && !trimmed.endsWith("}");
        }

        private PortfolioAdviceResponse fallbackAdviceResponse(String aiResponse) {
                String safeResponse = aiResponse == null ? "" : aiResponse;
                return new PortfolioAdviceResponse(
                                "Unable to parse AI response. Raw response: "
                                                + safeResponse.substring(0, Math.min(200, safeResponse.length())),
                                "Unable to parse structured response",
                                List.of("Review portfolio manually", "Consult with financial advisor", "Monitor risk metrics closely"),
                                "AI response parsing failed - manual review required");
        }

        private PortfolioAdviceResponse normalizeAdviceResponse(PortfolioAdviceResponse response) {
                if (response == null) {
                        return new PortfolioAdviceResponse(
                                        "Risk overview is unavailable from AI output.",
                                        "Diversification feedback is unavailable from AI output.",
                                        List.of("Review portfolio concentration and rebalance", "Diversify across sectors and market-cap buckets", "Re-check flagged holdings before new entries"),
                                        "Use this advice as guidance only and validate with deterministic metrics.");
                }

                String riskOverview = sanitizeText(
                                response.riskOverview(),
                                "Risk overview is unavailable from AI output.");
                String diversificationFeedback = sanitizeText(
                                response.diversificationFeedback(),
                                "Diversification feedback is unavailable from AI output.");
                String cautionaryNote = sanitizeText(
                                response.cautionaryNote(),
                                "Use this advice as guidance only and validate with deterministic metrics.");

                List<String> suggestions = response.suggestions() == null
                                ? List.of()
                                : response.suggestions().stream()
                                                .filter(s -> s != null && !s.isBlank())
                                                .map(String::trim)
                                                .toList();

                if (suggestions.isEmpty()) {
                        suggestions = List.of(
                                        "Review portfolio concentration and rebalance",
                                        "Diversify across sectors and market-cap buckets",
                                        "Re-check flagged holdings before new entries");
                } else if (suggestions.size() > 3) {
                        suggestions = suggestions.subList(0, 3);
                }

                return new PortfolioAdviceResponse(riskOverview, diversificationFeedback, suggestions, cautionaryNote);
        }

        private String sanitizeText(String value, String fallback) {
                if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
                        return fallback;
                }
                return value.trim();
        }

        private PortfolioAdviceResponse tryExtractSuggestionsFromMalformedJson(String aiResponse) {
                try {
                        int suggestionsStart = aiResponse.indexOf("\"suggestions\":");
                        if (suggestionsStart == -1) {
                                return null;
                        }

                        int arrayStart = aiResponse.indexOf('[', suggestionsStart);
                        int arrayEnd = aiResponse.indexOf(']', arrayStart);

                        if (arrayStart == -1 || arrayEnd == -1) {
                                return null;
                        }

                        String suggestionsArray = aiResponse.substring(arrayStart, arrayEnd + 1);

                        List<String> suggestions = objectMapper.readValue(
                                        suggestionsArray,
                                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

                        suggestions = suggestions.stream()
                                        .filter(s -> !s.contains(":") && !s.trim().isEmpty())
                                        .toList();

                        if (!suggestions.isEmpty()) {
                                return new PortfolioAdviceResponse(
                                                extractFieldFromJson(aiResponse, "risk_overview"),
                                                extractFieldFromJson(aiResponse, "diversification_feedback"),
                                                suggestions,
                                                extractFieldFromJson(aiResponse, "cautionary_note"));
                        }

                } catch (Exception e) {
                        logger.warning("Failed to extract suggestions from malformed JSON: " + e.getMessage());
                }
                return null;
        }

        private String extractFieldFromJson(String json, String fieldName) {
                try {
                        int fieldStart = json.indexOf("\"" + fieldName + "\":");
                        if (fieldStart == -1) {
                                return "Unable to extract " + fieldName;
                        }

                        int valueStart = json.indexOf('"', fieldStart + fieldName.length() + 3);
                        if (valueStart == -1) {
                                return "Unable to extract " + fieldName;
                        }

                        int valueEnd = json.indexOf('"', valueStart + 1);
                        if (valueEnd == -1) {
                                return "Unable to extract " + fieldName;
                        }

                        return json.substring(valueStart + 1, valueEnd);
                } catch (Exception e) {
                        return "Unable to extract " + fieldName;
                }
        }

        private record AdvisorCallResult(String aiResponse, long llmTimeMs) {
        }
}
