package com.cursor_springa_ai.playground.ai.tools;

import com.cursor_springa_ai.playground.dto.ai.AnalysisSnapshot;
import com.cursor_springa_ai.playground.dto.ai.PortfolioStatsSummary;
import com.cursor_springa_ai.playground.model.entity.AiAnalysis;
import com.cursor_springa_ai.playground.model.AnalysisType;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.enums.ConcentrationLevel;
import com.cursor_springa_ai.playground.model.enums.DiversificationLevel;
import com.cursor_springa_ai.playground.model.enums.PerformanceLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioRiskLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioStyle;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortfolioChatReasoningToolsTest {

    private static final TypeReference<List<Map<String, String>>> CHAT_HISTORY_TYPE = new TypeReference<>() { };
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recentChatHistory_keepsNewestFirstOrdering() throws Exception {
        PortfolioChatReasoningTools tools = new PortfolioChatReasoningTools(
                sampleSnapshot(),
                List.of(
                        new AiAnalysis(null, AnalysisType.PORTFOLIO_CHAT, "Latest question", "{\"answer\":\"Latest answer\"}", null, null, 10L, "model", "V1"),
                        new AiAnalysis(null, AnalysisType.PORTFOLIO_CHAT, "Earlier question", "{\"answer\":\"Earlier answer\"}", null, null, 10L, "model", "V1")
                ),
                objectMapper
        );

        List<Map<String, String>> history = objectMapper.readValue(tools.recentChatHistory(), CHAT_HISTORY_TYPE);

        assertEquals("Latest question", history.getFirst().get("question"));
        assertEquals("Earlier question", history.get(1).get("question"));
    }

    private AnalysisSnapshot sampleSnapshot() {
        return new AnalysisSnapshot(
                new PortfolioClassification(
                        PortfolioRiskLevel.HIGH,
                        DiversificationLevel.AVERAGE,
                        ConcentrationLevel.CONCENTRATED,
                        PerformanceLevel.GOOD,
                        PortfolioStyle.GROWTH_HEAVY,
                        BigDecimal.valueOf(5),
                        BigDecimal.valueOf(62)
                ),
                new PortfolioStatsSummary(
                        6,
                        BigDecimal.valueOf(35),
                        BigDecimal.valueOf(64),
                        BigDecimal.valueOf(20),
                        BigDecimal.valueOf(28)
                ),
                List.of("HIGH_CONCENTRATION"),
                List.of(),
                List.of()
        );
    }
}
