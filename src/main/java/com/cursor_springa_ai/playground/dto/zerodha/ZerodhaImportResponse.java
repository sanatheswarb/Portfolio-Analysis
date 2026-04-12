package com.cursor_springa_ai.playground.dto.zerodha;

import java.math.BigDecimal;
import java.util.List;

public record ZerodhaImportResponse(
        String portfolioUserId,
        int importedHoldings,
        BigDecimal totalCurrentValue,
        List<String> symbols
) {
}

