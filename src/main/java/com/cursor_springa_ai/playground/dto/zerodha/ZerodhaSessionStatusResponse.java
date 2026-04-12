package com.cursor_springa_ai.playground.dto.zerodha;

public record ZerodhaSessionStatusResponse(
        boolean authenticated,
        String hint
) {
}

