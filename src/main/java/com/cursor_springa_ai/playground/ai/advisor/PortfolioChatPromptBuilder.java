package com.cursor_springa_ai.playground.ai.advisor;

import com.cursor_springa_ai.playground.dto.ai.AnalysisSnapshot;
import com.cursor_springa_ai.playground.model.AiAnalysis;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PortfolioChatPromptBuilder {

    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() { };

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
                Use snapshot first. Use tools only if needed.

                QUESTION:
                %s
                """.formatted(
                formatSnapshot(snapshot),
                formatChats(chats),
                question == null ? "" : question.trim());
    }

    private String formatSnapshot(AnalysisSnapshot snapshot) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);
        } catch (Exception e) {
            return String.valueOf(snapshot);
        }
    }

    private String formatChats(List<AiAnalysis> chats) {
        if (chats == null || chats.isEmpty()) {
            return "No recent conversation.";
        }

        StringBuilder sb = new StringBuilder();
        List<AiAnalysis> orderedChats = new ArrayList<>(chats);
        java.util.Collections.reverse(orderedChats);
        for (AiAnalysis chat : orderedChats) {
            sb.append("User: ").append(chat.getQuestion() == null ? "" : chat.getQuestion()).append("\n");
            sb.append("AI: ").append(extractAnswer(chat)).append("\n");
        }
        return sb.toString().trim();
    }

    private String extractAnswer(AiAnalysis chat) {
        try {
            Map<String, Object> map = objectMapper.readValue(chat.getAnalysisData(), STRING_OBJECT_MAP);
            Object answer = map.get("answer");
            return answer == null ? "" : answer.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
