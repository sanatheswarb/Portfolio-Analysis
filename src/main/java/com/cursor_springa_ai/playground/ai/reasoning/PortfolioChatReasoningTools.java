package com.cursor_springa_ai.playground.ai.reasoning;

import com.cursor_springa_ai.playground.dto.ai.AnalysisSnapshot;
import com.cursor_springa_ai.playground.model.AiAnalysis;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class PortfolioChatReasoningTools {

    private static final Logger logger = Logger.getLogger(PortfolioChatReasoningTools.class.getName());
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() { };

    private final AnalysisSnapshot snapshot;
    private final List<AiAnalysis> chats;
    private final ObjectMapper objectMapper;
    private final List<String> toolInvocationOrder = new ArrayList<>();
    private final Map<String, Integer> toolInvocationCounts = new LinkedHashMap<>();
    private String snapshotOverviewCache;
    private String topHoldingsCache;
    private String sectorExposureCache;
    private String recentChatsCache;

    public PortfolioChatReasoningTools(AnalysisSnapshot snapshot, List<AiAnalysis> chats, ObjectMapper objectMapper) {
        this.snapshot = snapshot;
        this.chats = chats == null ? List.of() : List.copyOf(chats);
        this.objectMapper = objectMapper;
    }

    @Tool(name = "snapshot_overview", description = "Returns the saved portfolio snapshot overview including classification, portfolio stats, and risk flags. Call this first.")
    public String snapshotOverview() {
        recordToolInvocation("snapshot_overview");
        if (snapshotOverviewCache != null) {
            return snapshotOverviewCache;
        }
        snapshotOverviewCache = toJson(Map.of(
                "classification", snapshot.classification(),
                "portfolioStats", snapshot.portfolioStats(),
                "riskFlags", snapshot.riskFlags()
        ));
        return snapshotOverviewCache;
    }

    @Tool(name = "top_holdings", description = "Returns the saved top holdings with allocation and risk flags.")
    public String topHoldings() {
        recordToolInvocation("top_holdings");
        if (topHoldingsCache != null) {
            return topHoldingsCache;
        }
        topHoldingsCache = toJson(snapshot.topHoldings());
        return topHoldingsCache;
    }

    @Tool(name = "sector_exposure", description = "Returns the saved sector exposure summary for the portfolio.")
    public String sectorExposure() {
        recordToolInvocation("sector_exposure");
        if (sectorExposureCache != null) {
            return sectorExposureCache;
        }
        sectorExposureCache = toJson(snapshot.sectorExposure());
        return sectorExposureCache;
    }

    @Tool(name = "recent_chat_history", description = "Returns the recent portfolio chat history with prior user questions and AI answers.")
    public String recentChatHistory() {
        recordToolInvocation("recent_chat_history");
        if (recentChatsCache != null) {
            return recentChatsCache;
        }

        List<Map<String, String>> history = chats.stream()
                .map(chat -> Map.of(
                        "question", chat.getQuestion() == null ? "" : chat.getQuestion(),
                        "answer", extractAnswer(chat)
                ))
                .toList();
        recentChatsCache = toJson(history);
        return recentChatsCache;
    }

    public int invocationCount() {
        return toolInvocationOrder.size();
    }

    public Map<String, Integer> invocationCounts() {
        return Map.copyOf(toolInvocationCounts);
    }

    public String firstInvokedTool() {
        return toolInvocationOrder.isEmpty() ? null : toolInvocationOrder.getFirst();
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize chat reasoning tool payload", exception);
        }
    }

    private void recordToolInvocation(String toolName) {
        String safeToolName = Objects.requireNonNull(toolName, "toolName must not be null");
        toolInvocationOrder.add(safeToolName);
        logger.info("Chat advisor tool invoked: " + safeToolName);

        Integer currentCount = toolInvocationCounts.get(safeToolName);
        if (currentCount == null) {
            toolInvocationCounts.put(safeToolName, 1);
            return;
        }
        toolInvocationCounts.put(safeToolName, currentCount + 1);
    }
}