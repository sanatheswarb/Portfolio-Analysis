package com.cursor_springa_ai.playground.ai.orchestration;

import com.cursor_springa_ai.playground.ai.advisor.PortfolioAdvisorAgent;
import com.cursor_springa_ai.playground.ai.persistence.AiAnalysisService;
import com.cursor_springa_ai.playground.dto.ChatResponse;
import com.cursor_springa_ai.playground.dto.ai.AnalysisSnapshot;
import com.cursor_springa_ai.playground.dto.ai.PortfolioStatsSummary;
import com.cursor_springa_ai.playground.model.entity.AiAnalysis;
import com.cursor_springa_ai.playground.model.AnalysisType;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.entity.User;
import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.model.enums.ConcentrationLevel;
import com.cursor_springa_ai.playground.model.enums.DiversificationLevel;
import com.cursor_springa_ai.playground.model.enums.PerformanceLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioRiskLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioStyle;
import com.cursor_springa_ai.playground.repository.AiAnalysisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioChatServiceTest {

    private final AiAnalysisRepository repo = mock(AiAnalysisRepository.class);
    private final PortfolioAdvisorAgent advisor = mock(PortfolioAdvisorAgent.class);
    private final AiAnalysisService aiAnalysisService = mock(AiAnalysisService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
        private final PortfolioReasoningContextFactory reasoningContextFactory = mock(PortfolioReasoningContextFactory.class);
        private final PortfolioChatService service = new PortfolioChatService(repo, advisor, aiAnalysisService, objectMapper, reasoningContextFactory);

    @Test
    void askQuestion_rejectsBlankQuestion() {
        User user = user();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.askQuestion(user, "  "));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Question cannot be empty", exception.getReason());
    }

    @Test
    void askQuestion_requiresExistingPortfolioAnalysis() {
        User user = user();
        when(repo.findTopByUserIdAndAnalysisTypeOrderByCreatedAtDesc(7L, AnalysisType.PORTFOLIO_ANALYSIS))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.askQuestion(user, "What changed?"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Run portfolio analysis first", exception.getReason());
    }

    @Test
    void askQuestion_rejectsIncompleteSavedAnalysis() {
        User user = user();
        AiAnalysis analysis = new AiAnalysis(
                user,
                AnalysisType.PORTFOLIO_ANALYSIS,
                null,
                "{\"risk_overview\":\"high\"}",
                null,
                null,
                null,
                "model",
                "V1"
        );
        ReflectionTestUtils.setField(analysis, "id", 5L);
        when(repo.findTopByUserIdAndAnalysisTypeOrderByCreatedAtDesc(7L, AnalysisType.PORTFOLIO_ANALYSIS))
                .thenReturn(Optional.of(analysis));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.askQuestion(user, "What changed?"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Portfolio analysis is incomplete or corrupted", exception.getReason());
    }

    @Test
    void askQuestion_loadsSnapshotHistoryAndPersistsChat() throws Exception {
        User user = user();
        AnalysisSnapshot snapshot = sampleSnapshot();
        AiAnalysis analysis = new AiAnalysis(
                user,
                AnalysisType.PORTFOLIO_ANALYSIS,
                null,
                "{\"risk_overview\":\"high\"}",
                objectMapper.writeValueAsString(snapshot),
                null,
                null,
                "model",
                "V1"
        );
        ReflectionTestUtils.setField(analysis, "id", 5L);
        AiAnalysis priorChat = new AiAnalysis(
                user,
                AnalysisType.PORTFOLIO_CHAT,
                "Earlier question",
                "{\"answer\":\"Earlier answer\"}",
                null,
                null,
                5L,
                "model",
                "V1"
        );
        AiAnalysis savedChat = new AiAnalysis(
                user,
                AnalysisType.PORTFOLIO_CHAT,
                "What changed?",
                "{\"answer\":\"Risk remains concentrated.\"}",
                null,
                null,
                5L,
                "model",
                "V1"
        );
        ReflectionTestUtils.setField(savedChat, "id", 11L);

        when(repo.findTopByUserIdAndAnalysisTypeOrderByCreatedAtDesc(7L, AnalysisType.PORTFOLIO_ANALYSIS))
                .thenReturn(Optional.of(analysis));
        when(repo.findTop3ByParentAnalysisIdAndAnalysisTypeOrderByCreatedAtDesc(5L, AnalysisType.PORTFOLIO_CHAT))
                .thenReturn(List.of(priorChat));
        when(reasoningContextFactory.build(user)).thenReturn(mock(PortfolioReasoningContext.class));
        when(advisor.answerQuestion(any(), any(), any(), eq("What changed?")))
                .thenReturn("Risk remains concentrated.");
        when(aiAnalysisService.saveChat(user, "What changed?", "Risk remains concentrated.", 5L))
                .thenReturn(savedChat);

        ChatResponse response = service.askQuestion(user, "What changed?");

        assertEquals("Risk remains concentrated.", response.getAnswer());
        assertEquals(savedChat.getId(), response.getChatId());
        assertEquals(analysis.getId(), response.getAnalysisId());
                verify(advisor).answerQuestion(any(), any(), eq(List.of(priorChat)), eq("What changed?"));
        verify(aiAnalysisService).saveChat(user, "What changed?", "Risk remains concentrated.", analysis.getId());
    }

    private User user() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        return user;
    }

    private AnalysisSnapshot sampleSnapshot() {
        return new AnalysisSnapshot(
                new PortfolioClassification(
                        PortfolioRiskLevel.HIGH,
                        DiversificationLevel.AVERAGE,
                        ConcentrationLevel.CONCENTRATED,
                        PerformanceLevel.GOOD,
                        PortfolioStyle.GROWTH_HEAVY,
                        BigDecimal.valueOf(7),
                        BigDecimal.valueOf(58)
                ),
                new PortfolioStatsSummary(
                        5,
                        BigDecimal.valueOf(32),
                        BigDecimal.valueOf(58),
                        BigDecimal.valueOf(16),
                        BigDecimal.valueOf(24)
                ),
                List.of("HIGH_CONCENTRATION"),
                List.of(),
                List.of()
        );
    }
}
