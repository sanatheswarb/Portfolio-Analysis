package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.ai.NewsImpact;
import com.cursor_springa_ai.playground.dto.ai.NewsItemDto;
import com.cursor_springa_ai.playground.dto.ai.NewsMateriality;
import com.cursor_springa_ai.playground.dto.external.FinnhubNewsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketNewsServiceTest {

    @Test
    void searchStockNews_returnsOnlyMaterialNewsSortedByMaterialityThenRecency() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        MarketNewsService service = new MarketNewsService(restTemplate, new NewsClassifier());
        ReflectionTestUtils.setField(service, "apiKey", "test-key");

        FinnhubNewsResponse[] response = new FinnhubNewsResponse[] {
                new FinnhubNewsResponse("Routine media mention", "SRC", "u1", 1700000200L),
                new FinnhubNewsResponse("Company reports earnings guidance", "SRC", "u2", 1700000300L),
                new FinnhubNewsResponse("Company under regulatory investigation", "SRC", "u3", 1700000400L),
                new FinnhubNewsResponse("Company signs major contract", "SRC", "u4", 1700000500L)
        };

        when(restTemplate.getForObject(anyString(), eq(FinnhubNewsResponse[].class))).thenReturn(response);

        List<NewsItemDto> result = service.searchStockNews("INFY");

        assertEquals(3, result.size());
        assertEquals(NewsMateriality.HIGH, result.getFirst().materiality());
        assertEquals("Company under regulatory investigation", result.getFirst().headline());
        assertEquals(NewsMateriality.MEDIUM, result.get(1).materiality());
        assertEquals("Company signs major contract", result.get(1).headline());
        assertEquals(NewsMateriality.MEDIUM, result.get(2).materiality());
        assertEquals("Company reports earnings guidance", result.get(2).headline());
        assertEquals(NewsImpact.NEGATIVE, result.getFirst().impact());
        assertTrue(result.getFirst().riskRelevant());
        assertFalse(result.get(1).riskRelevant());

        String expectedFrom = LocalDate.now(ZoneOffset.UTC).minusDays(30).toString();
        String expectedTo = LocalDate.now(ZoneOffset.UTC).toString();
        verify(restTemplate).getForObject(
                org.mockito.ArgumentMatchers.contains("symbol=INFY"),
                eq(FinnhubNewsResponse[].class)
        );
        verify(restTemplate).getForObject(
                org.mockito.ArgumentMatchers.contains("from=" + expectedFrom),
                eq(FinnhubNewsResponse[].class)
        );
        verify(restTemplate).getForObject(
                org.mockito.ArgumentMatchers.contains("to=" + expectedTo),
                eq(FinnhubNewsResponse[].class)
        );
        verify(restTemplate).getForObject(
                org.mockito.ArgumentMatchers.contains("token=test-key"),
                eq(FinnhubNewsResponse[].class)
        );
    }

    @Test
    void searchStockNews_fallsBackToTopThreeWhenOnlyLowMaterialityExists() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        MarketNewsService service = new MarketNewsService(restTemplate, new NewsClassifier());
        ReflectionTestUtils.setField(service, "apiKey", "test-key");

        FinnhubNewsResponse[] response = new FinnhubNewsResponse[] {
                new FinnhubNewsResponse("Routine update A", "SRC", "u1", 1700000200L),
                new FinnhubNewsResponse("Routine update B", "SRC", "u2", 1700000300L),
                new FinnhubNewsResponse("Routine update C", "SRC", "u3", 1700000400L),
                new FinnhubNewsResponse("Routine update D", "SRC", "u4", 1700000500L)
        };

        when(restTemplate.getForObject(anyString(), eq(FinnhubNewsResponse[].class))).thenReturn(response);

        List<NewsItemDto> result = service.searchStockNews("INFY");

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(n -> n.materiality() == NewsMateriality.LOW));
    }
}
