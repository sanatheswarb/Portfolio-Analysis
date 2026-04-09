package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.ai.NewsImpact;
import com.cursor_springa_ai.playground.dto.ai.NewsItemDto;
import com.cursor_springa_ai.playground.dto.ai.NewsMateriality;
import com.cursor_springa_ai.playground.dto.external.FinnhubNewsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
public class MarketNewsService {

    private final RestTemplate restTemplate;
    private final NewsClassifier classifier;

    @Value("${finnhub.api.key}")
    private String apiKey;

    public MarketNewsService(RestTemplate restTemplate, NewsClassifier classifier) {
        this.restTemplate = restTemplate;
        this.classifier = classifier;
    }

    public List<NewsItemDto> searchStockNews(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return List.of();
        }

        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        String from = now.minusDays(30).toString();
        String to = now.toString();

        String url = UriComponentsBuilder
                .fromHttpUrl("https://finnhub.io/api/v1/company-news")
                .queryParam("symbol", symbol)
                .queryParam("from", from)
                .queryParam("to", to)
                .queryParam("token", apiKey)
                .build()
                .toUriString();

        FinnhubNewsResponse[] response = restTemplate.getForObject(url, FinnhubNewsResponse[].class);
        if (response == null || response.length == 0) {
            return List.of();
        }

        List<NewsItemDto> news = Arrays.stream(response)
                .map(this::toNewsItem)
                .sorted(Comparator
                        .comparingInt((NewsItemDto n) -> n.materiality().priority())
                        .reversed()
                        .thenComparing(NewsItemDto::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<NewsItemDto> materialNews = news.stream()
                .filter(n -> n.materiality() != NewsMateriality.LOW)
                .limit(5)
                .toList();

        if (!materialNews.isEmpty()) {
            return materialNews;
        }

        return news.stream()
                .limit(3)
                .toList();
    }

    private NewsItemDto toNewsItem(FinnhubNewsResponse response) {
        String headline = response.headline() == null ? "" : response.headline();
        NewsImpact impact = classifier.classifyImpact(headline);
        NewsMateriality materiality = classifier.classifyMateriality(headline);
        boolean riskRelevant = classifier.isRiskRelevant(impact, materiality);

        String publishedAt = response.datetime() == null
                ? null
                : Instant.ofEpochSecond(response.datetime()).toString();

        return new NewsItemDto(
                headline,
                response.source(),
                publishedAt,
                response.url(),
                impact,
                materiality,
                riskRelevant
        );
    }
}
