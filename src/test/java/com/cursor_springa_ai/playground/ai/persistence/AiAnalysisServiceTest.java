package com.cursor_springa_ai.playground.ai.persistence;

import com.cursor_springa_ai.playground.dto.PortfolioAdviceResponse;
import com.cursor_springa_ai.playground.ai.dto.AnalysisDecisionTrace;
import com.cursor_springa_ai.playground.ai.dto.AnalysisSnapshot;
import com.cursor_springa_ai.playground.ai.dto.PortfolioStatsSummary;
import com.cursor_springa_ai.playground.model.entity.AiAnalysis;
import com.cursor_springa_ai.playground.model.enums.AnalysisType;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.enums.ConcentrationLevel;
import com.cursor_springa_ai.playground.model.enums.DiversificationLevel;
import com.cursor_springa_ai.playground.model.enums.PerformanceLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioRiskLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioStyle;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cursor_springa_ai.playground.repository.AiAnalysisRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAnalysisServiceTest {

    private final AiAnalysisRepository repository = mock(AiAnalysisRepository.class);
    private final AiAnalysisService service = new AiAnalysisService(repository, "qwen2.5:7b-instruct", new ObjectMapper());

    @Test
    void savePortfolioAdvice_persistsRowWithCorrectType() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.savePortfolioAdvice(null, sampleAdvice(), sampleSnapshot(), sampleTrace());

        ArgumentCaptor<AiAnalysis> captor = ArgumentCaptor.forClass(AiAnalysis.class);
        verify(repository).save(captor.capture());
        AiAnalysis saved = captor.getValue();

        assertEquals(AnalysisType.PORTFOLIO_ANALYSIS, saved.getAnalysisType());
        assertNull(saved.getQuestion());
        assertNotNull(saved.getAnalysisData());
        assertNotNull(saved.getAnalysisContext());
    }

    @Test
    void savePortfolioAdvice_storesModelAndVersion() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.savePortfolioAdvice(null, sampleAdvice(), sampleSnapshot(), sampleTrace());

        ArgumentCaptor<AiAnalysis> captor = ArgumentCaptor.forClass(AiAnalysis.class);
        verify(repository).save(captor.capture());
        AiAnalysis saved = captor.getValue();

        assertEquals("qwen2.5:7b-instruct", saved.getModelUsed());
        assertEquals(AiAnalysisService.ANALYSIS_VERSION, saved.getAnalysisVersion());
    }

    @Test
    void saveChat_persistsChatWithParentAnalysisId() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AiAnalysis saved = service.saveChat(null, "What changed?", "Risk is concentrated.", 42L);

        assertEquals(AnalysisType.PORTFOLIO_CHAT, saved.getAnalysisType());
        assertEquals("What changed?", saved.getQuestion());
        assertEquals(42L, saved.getParentAnalysisId());
        assertTrue(saved.getAnalysisData().contains("Risk is concentrated."));
    }

    @Test
    void savePortfolioAdvice_storesSnapshotJson() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.savePortfolioAdvice(null, sampleAdvice(), sampleSnapshot(), null);

        ArgumentCaptor<AiAnalysis> captor = ArgumentCaptor.forClass(AiAnalysis.class);
        verify(repository).save(captor.capture());

        String context = captor.getValue().getAnalysisContext();
        assertNotNull(context);
        assertTrue(context.contains("HIGH_CONCENTRATION"));
    }

    @Test
    void savePortfolioAdvice_storesTraceJson() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.savePortfolioAdvice(null, sampleAdvice(), null, sampleTrace());

        ArgumentCaptor<AiAnalysis> captor = ArgumentCaptor.forClass(AiAnalysis.class);
        verify(repository).save(captor.capture());

        String trace = captor.getValue().getAnalysisTrace();
        assertNotNull(trace);
        assertTrue(trace.contains("HIGH_CONCENTRATION"));
        assertTrue(trace.contains("INFY"));
    }

    @Test
    void savePortfolioAdvice_nullSnapshotStoresNullContext() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.savePortfolioAdvice(null, sampleAdvice(), null, null);

        ArgumentCaptor<AiAnalysis> captor = ArgumentCaptor.forClass(AiAnalysis.class);
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getAnalysisContext());
    }

    @Test
    void savePortfolioAdvice_nullTraceStoresNullTrace() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.savePortfolioAdvice(null, sampleAdvice(), sampleSnapshot(), null);

        ArgumentCaptor<AiAnalysis> captor = ArgumentCaptor.forClass(AiAnalysis.class);
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getAnalysisTrace());
    }

    @Test
    void savePortfolioAdvice_doesNothingWhenAdviceIsNull() {
        service.savePortfolioAdvice(null, null, sampleSnapshot(), sampleTrace());

        verify(repository, never()).save(any());
    }

    private AnalysisDecisionTrace sampleTrace() {
        return new AnalysisDecisionTrace(
                "HIGH_CONCENTRATION",
                "INFY",
                List.of("INFY", "TCS"),
                "Top 3 holdings exceed 65%",
                "Portfolio profitable"
        );
    }

    // ---- helpers ----

    private PortfolioAdviceResponse sampleAdvice() {
        return new PortfolioAdviceResponse(
                "Risk overview", "Diversification feedback",
                List.of("Reduce concentration"), "Caution");
    }

    private AnalysisSnapshot sampleSnapshot() {
        PortfolioClassification classification = new PortfolioClassification(
                PortfolioRiskLevel.HIGH, DiversificationLevel.AVERAGE,
                ConcentrationLevel.CONCENTRATED, PerformanceLevel.GOOD,
                PortfolioStyle.GROWTH_HEAVY, BigDecimal.valueOf(5), BigDecimal.valueOf(65));
        PortfolioStatsSummary stats = new PortfolioStatsSummary(
                10, BigDecimal.valueOf(32), BigDecimal.valueOf(68),
                BigDecimal.valueOf(18), BigDecimal.valueOf(28));
        return new AnalysisSnapshot(classification, stats,
                List.of("HIGH_CONCENTRATION"), List.of(), List.of());
    }
}
