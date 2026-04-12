package com.cursor_springa_ai.playground.importer;

import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaHoldingItem;
import com.cursor_springa_ai.playground.model.entity.Instrument;
import com.cursor_springa_ai.playground.model.entity.User;
import com.cursor_springa_ai.playground.model.entity.UserHolding;
import com.cursor_springa_ai.playground.service.InstrumentEnrichmentService;
import com.cursor_springa_ai.playground.service.StockFundamentalsService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
@Service
public class HoldingPreparationService {

    private final InstrumentEnrichmentService instrumentEnrichmentService;
    private final StockFundamentalsService stockFundamentalsService;
    private final HoldingValueCalculator holdingValueCalculator;

    public HoldingPreparationService(
            InstrumentEnrichmentService instrumentEnrichmentService,
            StockFundamentalsService stockFundamentalsService,
            HoldingValueCalculator holdingValueCalculator
    ) {
        this.instrumentEnrichmentService = instrumentEnrichmentService;
        this.stockFundamentalsService = stockFundamentalsService;
        this.holdingValueCalculator = holdingValueCalculator;
    }

    boolean isImportableHolding(ZerodhaHoldingItem item) {
        return item.getTradingSymbol() != null
                && item.getQuantity() != null
                && item.getQuantity().compareTo(BigDecimal.ZERO) > 0;
    }

    BigDecimal computeTotalCurrentValue(java.util.List<ZerodhaHoldingItem> activeHoldings) {
        return holdingValueCalculator.computeTotalCurrentValue(activeHoldings);
    }

    PreparedHolding prepareHolding(User currentUser,
                                   ZerodhaHoldingItem item,
                                   BigDecimal totalCurrentValue) {
        String symbol = TradingSymbolNormalizer.normalize(item.getTradingSymbol());
        Instrument instrument = instrumentEnrichmentService.upsertAndEnrich(item);
        if (instrument == null) {
            throw new IllegalStateException("Instrument resolution failed for symbol " + symbol);
        }

        BigDecimal previousClose = stockFundamentalsService.upsertIfStale(instrument);
        HoldingComputedValues values = holdingValueCalculator.computeValues(
                item,
                totalCurrentValue,
                previousClose
        );
        UserHolding userHolding = buildUserHolding(currentUser, instrument, values);
        return new PreparedHolding(symbol, userHolding);
    }

    private UserHolding buildUserHolding(User user, Instrument instrument, HoldingComputedValues values) {
        UserHolding holding = new UserHolding(
                user,
                instrument,
                values.quantity(),
                values.avgPrice(),
                values.closePrice(),
                values.lastPrice(),
                values.investedValue(),
                values.currentValue(),
                values.pnl(),
                values.pnlPercent(),
                values.dayChange(),
                values.dayChangePct()
        );
        holding.setWeightPercent(values.weightPercent());
        holding.setSymbol(values.symbol());
        return holding;
    }
}
