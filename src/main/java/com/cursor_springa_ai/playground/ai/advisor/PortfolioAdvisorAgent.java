package com.cursor_springa_ai.playground.ai.advisor;

import com.cursor_springa_ai.playground.ai.tools.MarketNewsTools;
import com.cursor_springa_ai.playground.dto.ai.AnalysisSnapshot;
import com.cursor_springa_ai.playground.model.AiAnalysis;
import com.cursor_springa_ai.playground.ai.reasoning.PortfolioChatReasoningTools;
import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningTools;
import com.cursor_springa_ai.playground.ai.reasoning.ToolInvocationRecorder;
import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.dto.ai.PortfolioDecisionHints;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

@Service
public class PortfolioAdvisorAgent {

        private static final Logger logger = Logger.getLogger(PortfolioAdvisorAgent.class.getName());
        private final ChatClient chatClient;
        private final ObjectMapper objectMapper;
        private final PortfolioAdvisorPromptBuilder promptBuilder;
        private final PortfolioChatPromptBuilder chatPromptBuilder;
        private final MarketNewsTools marketNewsTools;

        @Value("${portfolio.advisor.model:qwen2.5:7b-instruct}")
        private String advisorModel;

        @Value("${portfolio.advisor.temperature:0.2}")
        private double temperature;

        @Value("${portfolio.advisor.num-predict:320}")
        private int numPredict;

        @Value("${portfolio.advisor.keep-alive:10m}")
        private String keepAlive;

        @Value("${portfolio.advisor.internal-tool-execution:true}")
        private boolean internalToolExecutionEnabled;

        @Value("${portfolio.advisor.thinking-enabled:false}")
        private boolean thinkingEnabled;

        public PortfolioAdvisorAgent(ChatClient.Builder chatClientBuilder,
                        ObjectMapper objectMapper,
                        PortfolioAdvisorPromptBuilder promptBuilder,
                        PortfolioChatPromptBuilder chatPromptBuilder,
                        MarketNewsTools marketNewsTools) {
                this.chatClient = chatClientBuilder.build();
                this.objectMapper = objectMapper;
                this.promptBuilder = promptBuilder;
                this.chatPromptBuilder = chatPromptBuilder;
                this.marketNewsTools = marketNewsTools;
        }

        public PortfolioAdviceResponse generateInsights(PortfolioReasoningContext reasoningContext) {
                String systemPrompt = promptBuilder.buildSystemPrompt();
                String userPrompt = promptBuilder.buildReasoningRequest(reasoningContext);

                AdvisorCallResult firstAttempt = callAdvisor("initial", reasoningContext, systemPrompt, userPrompt, numPredict, temperature);
                PortfolioAdviceResponse parsedFirstAttempt = parseAdviceResponse(firstAttempt.aiResponse(), true);
                if (parsedFirstAttempt != null) {
                        return parsedFirstAttempt;
                }

                int retryNumPredict = Math.max(numPredict + 64, 192);
                double retryTemperature = Math.min(temperature, 0.1d);
                String retryUserPrompt = promptBuilder.buildRetryReasoningRequest(userPrompt);
                logger.info("Retrying advisor response with higher token budget after truncated JSON response");

                AdvisorCallResult secondAttempt = callAdvisor(
                                "retry",
                                reasoningContext,
                                systemPrompt,
                                retryUserPrompt,
                                retryNumPredict,
                                retryTemperature);
                PortfolioAdviceResponse parsedSecondAttempt = parseAdviceResponse(secondAttempt.aiResponse(), false);
                if (parsedSecondAttempt != null) {
                        return parsedSecondAttempt;
                }

                if (secondAttempt.aiResponse() == null || secondAttempt.aiResponse().isBlank()) {
                        logger.warning("Advisor returned no usable response after retry; using deterministic fallback advice.");
                        return blankResponseFallback(reasoningContext);
                }

                return fallbackAdviceResponse(secondAttempt.aiResponse());
        }

        public String answerQuestion(AnalysisSnapshot snapshot, PortfolioReasoningContext reasoningContext, List<AiAnalysis> chats, String question) {
                String prompt = chatPromptBuilder.buildPrompt(snapshot, chats, question);
                ToolInvocationRecorder toolInvocationRecorder = new ToolInvocationRecorder();
                PortfolioChatReasoningTools reasoningTools = new PortfolioChatReasoningTools(
                                snapshot,
                                chats,
                                objectMapper,
                                toolInvocationRecorder);
                PortfolioReasoningTools portfolioReasoningTools = new PortfolioReasoningTools(
                                reasoningContext,
                                objectMapper,
                                toolInvocationRecorder);

                long startTime = System.currentTimeMillis();
                String aiResponse = chatClient.prompt()
                                .user(prompt)
                                .tools(reasoningTools, portfolioReasoningTools, marketNewsTools)
                                .options(buildOptions(numPredict, temperature))
                                .call()
                                .content();
                long llmTime = System.currentTimeMillis() - startTime;
                String firstTool = toolInvocationRecorder.firstInvokedTool();
                java.util.Map<String, Integer> invocationCounts = mergeInvocationCounts(reasoningTools, portfolioReasoningTools);
                int totalCalls = reasoningTools.invocationCount() + portfolioReasoningTools.invocationCount();
                logger.info("Chat LLM time: " + llmTime + " ms");
                logger.info("Chat advisor tool usage summary: firstTool="
                                + firstTool
                                + ", totalCalls=" + totalCalls
                                + ", counts=" + invocationCounts);


                if (aiResponse == null || aiResponse.isBlank()) {
                        return "I could not generate a follow-up answer from the saved portfolio analysis.";
                }
                return aiResponse.trim();
        }

        

        private java.util.Map<String, Integer> mergeInvocationCounts(
                        PortfolioChatReasoningTools chatTools,
                        PortfolioReasoningTools portfolioTools) {
                java.util.LinkedHashMap<String, Integer> merged = new java.util.LinkedHashMap<>();
                merged.putAll(chatTools.invocationCounts());
                portfolioTools.invocationCounts().forEach((tool, count) -> merged.merge(tool, count,
                                (existingCount, newCount) -> existingCount + newCount));
                return java.util.Map.copyOf(merged);
        }

        private AdvisorCallResult callAdvisor(
                        String attemptLabel,
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
                logAdvisorRawResponse(attemptLabel, aiResponse);
                validateRequiredToolUsage(reasoningTools);
                logger.info("Advisor tool usage summary: firstTool=" + reasoningTools.firstInvokedTool()
                                + ", totalCalls=" + reasoningTools.invocationCount()
                                + ", counts=" + reasoningTools.invocationCounts());
                logRepeatedOverviewUsage(reasoningTools);
                logMissingDrillDownUsage(reasoningTools);
                return new AdvisorCallResult(aiResponse, llmTime);
        }

        private void logAdvisorRawResponse(String attemptLabel, String aiResponse) {
                if (aiResponse == null) {
                        logger.warning("Advisor raw response [" + attemptLabel + "] is null before parsing.");
                        return;
                }

                logger.info("Advisor raw response [" + attemptLabel + "] chars=" + aiResponse.length()
                                + "\n--- LLM RESPONSE START ---\n"
                                + aiResponse
                                + "\n--- LLM RESPONSE END ---");
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
                String normalizedResponse = normalizeAiResponse(aiResponse);
                if (normalizedResponse == null || normalizedResponse.isBlank()) {
                        return null;
                }
                try {
                        PortfolioAdviceResponse parsed = objectMapper.readValue(normalizedResponse, PortfolioAdviceResponse.class);
                        return normalizeAdviceResponse(parsed);
                } catch (Exception e) {
                        if (retryOnTruncation && isLikelyTruncatedResponse(normalizedResponse, e)) {
                                return null;
                        }
                        logger.warning("Failed to parse AI response as JSON: " + e.getMessage());
                        logger.warning("Raw AI response: " + normalizedResponse);

                        PortfolioAdviceResponse fallback = tryExtractSuggestionsFromMalformedJson(normalizedResponse);
                        if (fallback != null) {
                                return normalizeAdviceResponse(fallback);
                        }
                        return fallbackAdviceResponse(normalizedResponse);
                }
        }

        private String normalizeAiResponse(String aiResponse) {
                if (aiResponse == null) {
                        return null;
                }

                String normalized = aiResponse.trim();
                if (normalized.isEmpty()) {
                        return normalized;
                }

                if (normalized.startsWith("```")) {
                        int firstLineBreak = normalized.indexOf('\n');
                        if (firstLineBreak != -1) {
                                normalized = normalized.substring(firstLineBreak + 1).trim();
                        }
                        if (normalized.endsWith("```")) {
                                normalized = normalized.substring(0, normalized.length() - 3).trim();
                        }
                }

                int firstBrace = normalized.indexOf('{');
                int lastBrace = normalized.lastIndexOf('}');
                if (firstBrace >= 0 && lastBrace > firstBrace) {
                        normalized = normalized.substring(firstBrace, lastBrace + 1).trim();
                }

                return normalized;
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

        private PortfolioAdviceResponse blankResponseFallback(PortfolioReasoningContext reasoningContext) {
                PortfolioDecisionHints hints = reasoningContext.decisionHints();

                String primaryRisk = hints != null && hints.primaryRisk() != null
                                ? humanizeToken(hints.primaryRisk())
                                : humanizeToken(firstRiskFlag(reasoningContext));

                String riskOverview;
                if (primaryRisk != null) {
                        riskOverview = "Portfolio risk remains centered on " + primaryRisk + " based on the current portfolio signals.";
                } else if (reasoningContext.classification() != null && reasoningContext.classification().riskLevel() != null) {
                        riskOverview = "Portfolio risk is currently assessed as "
                                        + humanizeToken(reasoningContext.classification().riskLevel().name())
                                        + " based on the available portfolio metrics.";
                } else {
                        riskOverview = "Portfolio risk could not be summarized by the AI model, so deterministic portfolio signals were used instead.";
                }

                String diversificationFeedback = buildDiversificationFallback(reasoningContext, hints);

                return new PortfolioAdviceResponse(
                                riskOverview,
                                diversificationFeedback,
                                buildFallbackSuggestions(reasoningContext, hints),
                                "AI advisor returned no usable response, so this fallback uses deterministic portfolio metrics only.");
        }

        private String buildDiversificationFallback(PortfolioReasoningContext reasoningContext,
                        PortfolioDecisionHints hints) {
                if (hints != null && hints.concentrationReductionNeeded()) {
                        BigDecimal largestHoldingPercent = hints.largestHoldingPercent();
                        String percentSuffix = largestHoldingPercent != null
                                        ? " with the largest holding at " + largestHoldingPercent.stripTrailingZeros().toPlainString() + "%"
                                        : "";
                        return "The portfolio is concentrated" + percentSuffix
                                        + ", so diversification should improve before adding new risk.";
                }

                if (hints != null && hints.diversificationNeeded()) {
                        return "Diversification is currently weak, so sector and market-cap balance should be improved.";
                }

                if (reasoningContext.classification() != null && reasoningContext.classification().diversificationLevel() != null) {
                        return "Diversification is currently assessed as "
                                        + humanizeToken(reasoningContext.classification().diversificationLevel().name())
                                        + " from the saved portfolio metrics.";
                }

                return "Diversification should be reviewed manually because the AI model returned no final portfolio narrative.";
        }

        private List<String> buildFallbackSuggestions(PortfolioReasoningContext reasoningContext,
                        PortfolioDecisionHints hints) {
                List<String> suggestions = new ArrayList<>();

                if (hints != null && hints.concentrationReductionNeeded()) {
                        suggestions.add("Reduce oversized single-stock concentration and rebalance the portfolio");
                }

                if (hints != null && hints.diversificationNeeded()) {
                        suggestions.add("Spread exposure across more sectors and market-cap buckets");
                }

                if (hints != null && hints.smallCapRiskHigh()) {
                        suggestions.add("Trim small-cap exposure until overall portfolio risk is lower");
                }

                if (reasoningContext.classification() != null
                                && reasoningContext.classification().riskLevel() != null
                                && "high".equals(humanizeToken(reasoningContext.classification().riskLevel().name()))) {
                        suggestions.add("Prioritize reducing the highest portfolio risk before seeking new upside");
                }

                if (suggestions.isEmpty()) {
                        suggestions.add("Review the top holdings and rebalance positions that dominate the portfolio");
                }
                if (suggestions.size() < 2) {
                        suggestions.add("Check whether sector and style exposure are aligned with your risk tolerance");
                }
                if (suggestions.size() < 3) {
                        suggestions.add("Use the saved risk flags and portfolio metrics before making the next change");
                }

                return suggestions.stream().limit(3).toList();
        }

        private String firstRiskFlag(PortfolioReasoningContext reasoningContext) {
                return reasoningContext.portfolioRiskFlags().stream().findFirst().orElse(null);
        }

        private String humanizeToken(String token) {
                if (token == null || token.isBlank()) {
                        return null;
                }
                return token.toLowerCase(Locale.ROOT).replace('_', ' ');
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
