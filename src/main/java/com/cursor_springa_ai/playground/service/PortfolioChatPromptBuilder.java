package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.model.AiAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PortfolioChatPromptBuilder {

    private final ObjectMapper objectMapper;

    public PortfolioChatPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String buildPrompt(AnalysisSnapshot snapshot, List<AiAnalysis> chats, String question) {
        return """
                You are answering follow-up portfolio questions.

                PORTFOLIO SNAPSHOT:
                %s

                RECENT CONVERSATION:
                %s

                TOOL RULES:
                Use snapshot first.
                Use tools only if needed.
                Reuse the existing portfolio tools if the question needs more detail.
                Stay grounded in the saved analysis context.
                Do not give generic investing advice unrelated to the portfolio.

                QUESTION:
                %s
                """.formatted(formatSnapshot(snapshot), formatChats(chats), question);
    }

    private String formatSnapshot(AnalysisSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("portfolioUserId", snapshot.portfolioUserId());
        payload.put("portfolioSummary", snapshot.portfolioSummary());
        payload.put("classification", snapshot.classification());
        payload.put("portfolioRiskFlags", snapshot.portfolioRiskFlags());
        payload.put("metrics", snapshot.metrics());
        payload.put("largestHoldings", snapshot.enrichedHoldings().stream().limit(3).toList());
        return toJson(payload);
    }

    private String formatChats(List<AiAnalysis> chats) {
        if (chats == null || chats.isEmpty()) {
            return "No recent conversation.";
        }

        StringBuilder builder = new StringBuilder();
        for (int index = chats.size() - 1; index >= 0; index--) {
            AiAnalysis chat = chats.get(index);
            builder.append("User: ")
                    .append(chat.getQuestion() == null ? "" : chat.getQuestion())
                    .append("\n");
            builder.append("AI: ")
                    .append(extractAnswer(chat))
                    .append("\n");
        }
        return builder.toString().trim();
    }

    private String extractAnswer(AiAnalysis chat) {
        try {
            Map<?, ?> map = objectMapper.readValue(chat.getAnalysisData(), Map.class);
            Object answer = map.get("answer");
            return answer == null ? "" : answer.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize chat prompt payload", exception);
        }
    }
}
