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
import java.util.List;
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

    boolean isImportableHolding(ZerodhaHoldingItem holdingItem) {
        String normalizedSymbol = StringNormalizer.normalize(holdingItem.getTradingSymbol());
        if (normalizedSymbol == null) {
            logger.warning("Skipping Zerodha holding with null or blank trading symbol");
            return false;
        }

        if (!importableSymbolPattern.matcher(normalizedSymbol).matches()) {
            logger.info("Skipping Zerodha holding with unsupported symbol: " + holdingItem.getTradingSymbol());
            return false;
        }

        if (holdingItem.getQuantity() == null || holdingItem.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            logger.info("Skipping Zerodha holding with zero or null quantity: " + holdingItem.getTradingSymbol());
            return false;
        }

        return true;
    }

    BigDecimal computeTotalCurrentValue(List<ZerodhaHoldingItem> importableHoldings) {
        return holdingValueCalculator.computeTotalCurrentValue(importableHoldings);
    }

    UserHolding prepareHolding(User currentUser,
                                   ZerodhaHoldingItem holdingItem,
                                   BigDecimal totalCurrentValue) {
        String symbol = StringNormalizer.normalize(holdingItem.getTradingSymbol());
        Instrument instrument = instrumentEnrichmentService.resolveInstrument(holdingItem);
        if (instrument == null) {
            throw new IllegalStateException("Instrument resolution failed for symbol " + symbol);
        }

        BigDecimal previousClose = stockFundamentalsService.refreshAndGetPreviousClose(instrument);
        HoldingComputedValues computedValues = holdingValueCalculator.computeValues(
                holdingItem,
                totalCurrentValue,
                previousClose
        );
        return buildUserHolding(currentUser, instrument, computedValues);
    }

    private UserHolding buildUserHolding(User user, Instrument instrument, HoldingComputedValues computedValues) {
        return new UserHolding(
                user,
                instrument,
                computedValues.symbol(),
                computedValues.quantity(),
                computedValues.avgPrice(),
                computedValues.closePrice(),
                computedValues.lastPrice(),
                computedValues.investedValue(),
                computedValues.currentValue(),
                computedValues.pnl(),
                computedValues.pnlPercent(),
                computedValues.dayChange(),
                computedValues.dayChangePct(),
                computedValues.weightPercent()
        );
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
