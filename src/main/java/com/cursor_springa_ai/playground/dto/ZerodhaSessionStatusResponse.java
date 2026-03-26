package com.cursor_springa_ai.playground.dto;

public record ZerodhaSessionStatusResponse(
        boolean authenticated,
        String hint
) {
}
