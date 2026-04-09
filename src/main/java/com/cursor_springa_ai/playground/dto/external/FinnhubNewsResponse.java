package com.cursor_springa_ai.playground.dto.external;

public record FinnhubNewsResponse(
        String headline,
        String source,
        String url,
        Long datetime
) {
}
