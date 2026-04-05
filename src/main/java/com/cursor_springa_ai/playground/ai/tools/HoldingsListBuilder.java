package com.cursor_springa_ai.playground.ai.tools;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.dto.ai.HoldingListItemDto;

import java.util.List;

public class HoldingsListBuilder {

    public List<HoldingListItemDto> build(PortfolioReasoningContext context) {
        return context.enrichedHoldings().stream()
                .sorted(HoldingClassifier.BY_ALLOCATION_DESC)
                .map(holding -> new HoldingListItemDto(
                        holding.symbol(),
                        holding.allocationPercent(),
                        holding.profitPercent(),
                        holding.valuationFlag(),
                        holding.riskFlags() != null ? holding.riskFlags() : List.of()
                ))
                .toList();
    }
}
