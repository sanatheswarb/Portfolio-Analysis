package com.cursor_springa_ai.playground.importer;

import com.cursor_springa_ai.playground.dto.zerodha.ZerodhaHoldingItem;
import com.cursor_springa_ai.playground.model.entity.Instrument;
import com.cursor_springa_ai.playground.model.entity.User;
import com.cursor_springa_ai.playground.model.entity.UserHolding;
import com.cursor_springa_ai.playground.service.InstrumentEnrichmentService;
import com.cursor_springa_ai.playground.service.StockFundamentalsService;
import com.cursor_springa_ai.playground.util.StringNormalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.logging.Logger;
@Service
public class HoldingPreparationService {

    private static final Logger logger = Logger.getLogger(HoldingPreparationService.class.getName());

    private final InstrumentEnrichmentService instrumentEnrichmentService;
    private final StockFundamentalsService stockFundamentalsService;
    private final HoldingValueCalculator holdingValueCalculator;
    private final Pattern importableSymbolPattern;

    public HoldingPreparationService(
            InstrumentEnrichmentService instrumentEnrichmentService,
            StockFundamentalsService stockFundamentalsService,
            HoldingValueCalculator holdingValueCalculator,
            @Value("${zerodha.import.symbol-pattern:^[A-Z0-9]+$}") String importableSymbolPattern
    ) {
        this.instrumentEnrichmentService = instrumentEnrichmentService;
        this.stockFundamentalsService = stockFundamentalsService;
        this.holdingValueCalculator = holdingValueCalculator;
        this.importableSymbolPattern = compileImportableSymbolPattern(importableSymbolPattern);
    }

    boolean isImportableHolding(ZerodhaHoldingItem item) {
        String normalizedSymbol = StringNormalizer.normalize(item.getTradingSymbol());
        if (normalizedSymbol == null) {
            logger.warning("Skipping Zerodha holding with null or blank trading symbol");
            return false;
        }

        if (!importableSymbolPattern.matcher(normalizedSymbol).matches()) {
            logger.info("Skipping Zerodha holding with unsupported symbol: " + item.getTradingSymbol());
            return false;
        }

        if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            logger.info("Skipping Zerodha holding with zero or null quantity: " + item.getTradingSymbol());
            return false;
        }

        return true;
    }

    BigDecimal computeTotalCurrentValue(java.util.List<ZerodhaHoldingItem> activeHoldings) {
        return holdingValueCalculator.computeTotalCurrentValue(activeHoldings);
    }

    PreparedHolding prepareHolding(User currentUser,
                                   ZerodhaHoldingItem item,
                                   BigDecimal totalCurrentValue) {
        String symbol = StringNormalizer.normalize(item.getTradingSymbol());
        Instrument instrument = instrumentEnrichmentService.resolveInstrument(item);
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

    private Pattern compileImportableSymbolPattern(String importableSymbolPattern) {
        try {
            return Pattern.compile(importableSymbolPattern);
        } catch (PatternSyntaxException exception) {
            throw new IllegalStateException(
                    "Invalid configuration for zerodha.import.symbol-pattern: " + importableSymbolPattern,
                    exception
            );
        }
    }
}
