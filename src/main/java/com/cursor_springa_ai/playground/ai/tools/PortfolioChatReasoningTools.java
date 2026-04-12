package com.cursor_springa_ai.playground.ai.tools;

import com.cursor_springa_ai.playground.ai.dto.AnalysisSnapshot;
import com.cursor_springa_ai.playground.model.entity.AiAnalysis;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.Map;

public class PortfolioChatReasoningTools {

    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() { };

    private final AnalysisSnapshot snapshot;
    private final List<AiAnalysis> chats;
    private final ObjectMapper objectMapper;
    private final ToolCallTracker toolCallTracker;
    private String snapshotOverviewCache;
    private String topHoldingsCache;
    private String sectorExposureCache;
    private String recentChatsCache;

    public PortfolioChatReasoningTools(AnalysisSnapshot snapshot, List<AiAnalysis> chats, ObjectMapper objectMapper) {
        this(snapshot, chats, objectMapper, null);
    }

    public PortfolioChatReasoningTools(AnalysisSnapshot snapshot,
                                       List<AiAnalysis> chats,
                                       ObjectMapper objectMapper,
                                       ToolInvocationRecorder toolInvocationRecorder) {
        this.snapshot = snapshot;
        this.chats = chats == null ? List.of() : List.copyOf(chats);
        this.objectMapper = objectMapper;
        this.toolCallTracker = new ToolCallTracker(
                PortfolioChatReasoningTools.class, "Chat advisor tool invoked: ", toolInvocationRecorder);
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
        return toolCallTracker.invocationCount();
    }

    public Map<String, Integer> invocationCounts() {
        return toolCallTracker.invocationCounts();
    }

    public String firstInvokedTool() {
        return toolCallTracker.firstInvokedTool();
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
        toolCallTracker.record(toolName);
    }
}
