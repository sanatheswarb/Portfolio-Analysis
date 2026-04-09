package com.cursor_springa_ai.playground.dto.ai;

public record NewsItemDto(
        String headline,
        String source,
        String publishedAt,
        String url,
        NewsImpact impact,
        NewsMateriality materiality,
        boolean riskRelevant
) {
}
