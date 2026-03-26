package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.dto.PortfolioMetrics;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.Portfolio;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.logging.Logger;

@Service
public class AiPortfolioAdvisorService {

        private static final Logger logger = Logger.getLogger(AiPortfolioAdvisorService.class.getName());
        private final ChatClient chatClient;
        private final ObjectMapper objectMapper;
        private final PortfolioAdvisorPromptBuilder promptBuilder;

        public AiPortfolioAdvisorService(ChatClient.Builder chatClientBuilder) {
                this.chatClient = chatClientBuilder.build();
                this.objectMapper = new ObjectMapper();
                this.promptBuilder = new PortfolioAdvisorPromptBuilder(this.objectMapper);
        }

        public PortfolioAdviceResponse generateInsightsWithMetrics(
                        Portfolio portfolio,
                        List<EnrichedHoldingData> enrichedHoldings,
                        PortfolioMetrics portfolioMetrics,
                        PortfolioSummary portfolioSummary) {
                String systemPrompt = promptBuilder.buildSystemPrompt();
                String userPrompt = promptBuilder.buildPortfolioDataWithMetrics(portfolio, enrichedHoldings, portfolioMetrics,
                                portfolioSummary);

                logger.info("System prompt length: " + systemPrompt.length());
                logger.info("User prompt length: " + userPrompt.length());

                OllamaChatOptions options = OllamaChatOptions.builder()
                                .model("qwen2.5:7b-instruct")
                                .temperature(0.2)
                                .numPredict(180)
                                .topP(0.9)
                                .stop(List.of("\n\n"))
                                .build();

                long startTime = System.currentTimeMillis();
                String aiResponse = chatClient.prompt()
                                .system(systemPrompt)
                                .user(userPrompt)
                                .options(options)
                                .call()
                                .content();
                long llmTime = System.currentTimeMillis() - startTime;
                logger.info("LLM time: " + llmTime + " ms");

                return parseAdviceResponse(aiResponse);
        }

        private PortfolioAdviceResponse parseAdviceResponse(String aiResponse) {
                try {
                        return objectMapper.readValue(aiResponse, PortfolioAdviceResponse.class);
                } catch (Exception e) {
                        logger.warning("Failed to parse AI response as JSON: " + e.getMessage());
                        logger.warning("Raw AI response: " + aiResponse);

                        // Try to extract suggestions from malformed JSON
                        PortfolioAdviceResponse fallback = tryExtractSuggestionsFromMalformedJson(aiResponse);
                        if (fallback != null) {
                                logger.info("Successfully extracted suggestions from malformed JSON");
                                return fallback;
                        }

                        // Return a fallback response with the raw text
                        return new PortfolioAdviceResponse(
                                        "Unable to parse AI response. Raw response: " + aiResponse.substring(0, Math.min(200, aiResponse.length())),
                                        "Unable to parse structured response",
                                        List.of("Review portfolio manually", "Consult with financial advisor", "Monitor risk metrics closely"),
                                        "AI response parsing failed - manual review required");
                }
        }

        private PortfolioAdviceResponse tryExtractSuggestionsFromMalformedJson(String aiResponse) {
                try {
                        // Look for suggestions array in the response
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

                        // Try to parse just the suggestions array
                        List<String> suggestions = objectMapper.readValue(suggestionsArray, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

                        // Filter out any non-string entries that might have been parsed
                        suggestions = suggestions.stream()
                                        .filter(s -> s instanceof String && !s.contains(":") && !s.trim().isEmpty())
                                        .map(Object::toString)
                                        .toList();

                        // If we have at least one valid suggestion, create a response
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

        

      
}
