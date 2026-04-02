package com.cursor_springa_ai.playground.ai.tools;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.ai.HoldingListItemDto;

import java.math.BigDecimal;
import java.util.List;

public class HoldingsListBuilder {

    public List<HoldingListItemDto> build(PortfolioReasoningContext context) {
        return context.enrichedHoldings().stream()
                .sorted((left, right) -> compareByAllocation(right, left))
                .map(holding -> new HoldingListItemDto(
                        holding.symbol(),
                        holding.allocationPercent(),
                        holding.profitPercent(),
                        holding.valuationFlag(),
                        holding.riskFlags() != null ? holding.riskFlags() : List.of()
                ))
                .toList();
    }

    private int compareByAllocation(EnrichedHoldingData left, EnrichedHoldingData right) {
        if (left.allocationPercent() == null && right.allocationPercent() == null) {
            return 0;
        }
        if (left.allocationPercent() == null) {
            return -1;
        }
        if (right.allocationPercent() == null) {
            return 1;
        }
        return left.allocationPercent().compareTo(right.allocationPercent());
    }
}
