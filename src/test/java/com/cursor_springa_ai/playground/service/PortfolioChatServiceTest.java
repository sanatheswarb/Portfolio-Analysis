package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.ChatResponse;
import com.cursor_springa_ai.playground.dto.PortfolioSummary;
import com.cursor_springa_ai.playground.model.AiAnalysis;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.repository.AiAnalysisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioChatServiceTest {

    @Test
    void askQuestion_usesLatestAnalysisSnapshotAndPersistsChat() throws Exception {
        AiAnalysisRepository repository = mock(AiAnalysisRepository.class);
        AiPortfolioAdvisorService advisor = mock(AiPortfolioAdvisorService.class);
        AiAnalysisService aiAnalysisService = mock(AiAnalysisService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PortfolioChatService service = new PortfolioChatService(repository, advisor, aiAnalysisService, objectMapper);

        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        AnalysisSnapshot snapshot = new AnalysisSnapshot(
                "portfolio-7",
                new PortfolioSummary(BigDecimal.valueOf(100), BigDecimal.valueOf(120), BigDecimal.valueOf(20), BigDecimal.valueOf(20), 2),
                null,
                List.of("HIGH_CONCENTRATION"),
                List.of(),
                null);

        AiAnalysis analysis = new AiAnalysis(
                user,
                AiAnalysisService.TYPE_PORTFOLIO_ANALYSIS,
                null,
                "{\"risk_overview\":\"risk\"}",
                objectMapper.writeValueAsString(snapshot),
                null,
                null,
                null);
        setId(analysis, 11L);
        AiAnalysis savedChat = new AiAnalysis(
                user,
                AiAnalysisService.TYPE_PORTFOLIO_CHAT,
                "What is the main risk?",
                "{\"answer\":\"Concentration is still high.\"}",
                null,
                1L,
                null,
                null);
        setId(savedChat, 21L);

        when(repository.findTopByUserIdAndAnalysisTypeOrderByCreatedAtDesc(7L, AiAnalysisService.TYPE_PORTFOLIO_ANALYSIS))
                .thenReturn(Optional.of(analysis));
        when(repository.findTop3ByParentAnalysisIdAndAnalysisTypeOrderByCreatedAtDesc(11L, AiAnalysisService.TYPE_PORTFOLIO_CHAT))
                .thenReturn(List.of());
        when(advisor.answerQuestion(eq(snapshot), eq(List.of()), eq("What is the main risk?")))
                .thenReturn("Concentration is still high.");
        when(aiAnalysisService.saveChat(user, "What is the main risk?", "Concentration is still high.", 11L))
                .thenReturn(savedChat);

        ChatResponse response = service.askQuestion(user, "What is the main risk?");

        assertEquals("Concentration is still high.", response.getAnswer());
        assertEquals(21L, response.getChatId());
        assertEquals(11L, response.getAnalysisId());
        verify(aiAnalysisService).saveChat(user, "What is the main risk?", "Concentration is still high.", 11L);
    }

    @Test
    void askQuestion_rejectsBlankQuestion() {
        PortfolioChatService service = new PortfolioChatService(
                mock(AiAnalysisRepository.class),
                mock(AiPortfolioAdvisorService.class),
                mock(AiAnalysisService.class),
                new ObjectMapper());
        User user = mock(User.class);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.askQuestion(user, " "));

        assertEquals("Question cannot be empty", exception.getMessage());
    }

    private void setId(AiAnalysis analysis, Long id) throws Exception {
        Field field = AiAnalysis.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(analysis, id);
    }
}
