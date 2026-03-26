package com.cursor_springa_ai.playground.dto;

import java.util.List;

public record ZerodhaImportResponse(
        String portfolioId,
        int importedHoldings,
        List<String> symbols
) {
}
