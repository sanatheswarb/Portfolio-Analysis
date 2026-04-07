package com.cursor_springa_ai.playground.importer;

import com.cursor_springa_ai.playground.controller.NotAuthenticatedException;
import com.cursor_springa_ai.playground.dto.ZerodhaImportResponse;
import com.cursor_springa_ai.playground.integration.zerodha.ZerodhaHoldingsClient;
import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaHoldingItem;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.model.UserHolding;
import com.cursor_springa_ai.playground.service.PortfolioStatsBatchService;
import com.cursor_springa_ai.playground.service.UserHoldingSyncService;
import com.cursor_springa_ai.playground.service.ZerodhaAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates the Zerodha holdings import pipeline.
 *
 * <p>Responsibilities (orchestration only — no financial calculations or persistence logic):
 * <ol>
 *   <li>Authenticate the current user.</li>
 *   <li>Fetch raw holdings from the Zerodha API.</li>
 *   <li>For each valid holding: enrich the instrument, refresh fundamentals, and compute values.</li>
 *   <li>Abort the entire import if any holding fails to process.</li>
 *   <li>Atomically replace all user holdings via {@link UserHoldingSyncService}.</li>
 *   <li>Trigger an async portfolio stats recalculation.</li>
 * </ol>
 *
 * <p>Holding preparation is delegated to {@link HoldingPreparationService}.
 * Duplicate consolidation is delegated to {@link HoldingMergeService}.
 * Persistence is delegated to {@link UserHoldingSyncService}.
 */
@Service
public class ZerodhaImportService {

    private static final Logger logger = Logger.getLogger(ZerodhaImportService.class.getName());

    private final ZerodhaHoldingsClient zerodhaHoldingsClient;
    private final ZerodhaAuthService zerodhaAuthService;
    private final UserHoldingSyncService userHoldingSyncService;
    private final PortfolioStatsBatchService portfolioStatsBatchService;
    private final HoldingPreparationService holdingPreparationService;
    private final HoldingMergeService holdingMergeService;
    private final Pattern importableSymbolPattern;

    public ZerodhaImportService(
            ZerodhaHoldingsClient zerodhaHoldingsClient,
            ZerodhaAuthService zerodhaAuthService,
            UserHoldingSyncService userHoldingSyncService,
            PortfolioStatsBatchService portfolioStatsBatchService,
            HoldingPreparationService holdingPreparationService,
            HoldingMergeService holdingMergeService,
            @Value("${zerodha.import.symbol-pattern:^[A-Z0-9]+$}") String importableSymbolPattern
    ) {
        this.zerodhaHoldingsClient = zerodhaHoldingsClient;
        this.zerodhaAuthService = zerodhaAuthService;
        this.userHoldingSyncService = userHoldingSyncService;
        this.portfolioStatsBatchService = portfolioStatsBatchService;
        this.holdingPreparationService = holdingPreparationService;
        this.holdingMergeService = holdingMergeService;
        this.importableSymbolPattern = compileImportableSymbolPattern(importableSymbolPattern);
    }

    public ZerodhaImportResponse importHoldings() {
        User currentUser = zerodhaAuthService.getCurrentUser();
        if (currentUser == null) {
            throw new NotAuthenticatedException(
                    "No authenticated Zerodha user found. Please complete login first.");
        }

        List<ZerodhaHoldingItem> incoming = zerodhaHoldingsClient.fetchHoldings();
        List<ZerodhaHoldingItem> normalizedIncoming = incoming.stream()
            .map(this::normalizeTradingSymbol)
            .toList();
        List<ZerodhaHoldingItem> filteredIncoming = normalizedIncoming.stream()
            .filter(this::hasSupportedTradingSymbol)
            .toList();
        List<ZerodhaHoldingItem> activeHoldings = filteredIncoming.stream()
                .filter(holdingPreparationService::isImportableHolding)
                .toList();
        BigDecimal totalCurrentValue = holdingPreparationService.computeTotalCurrentValue(activeHoldings);
        List<UserHolding> holdingsToPersist = holdingMergeService.mergeDuplicateHoldings(
            prepareHoldingsOrThrow(currentUser, activeHoldings, totalCurrentValue)
        );
        userHoldingSyncService.replaceHoldings(currentUser.getId(), holdingsToPersist);
        portfolioStatsBatchService.calculateForUserAsync(currentUser.getId());

        List<String> importedSymbols = holdingsToPersist.stream()
                .map(UserHolding::getSymbol)
                .toList();

        return new ZerodhaImportResponse(
                currentUser.getBrokerUserId(),
                importedSymbols.size(),
                importedSymbols
        );
    }

    private List<PreparedHolding> prepareHoldingsOrThrow(User currentUser,
                                                         List<ZerodhaHoldingItem> activeHoldings,
                                                         BigDecimal totalCurrentValue) {
        List<PreparedHolding> preparedHoldings = new ArrayList<>();
        List<String> failedSymbols = new ArrayList<>();
        List<RuntimeException> failedExceptions = new ArrayList<>();

        for (ZerodhaHoldingItem item : activeHoldings) {
            try {
                preparedHoldings.add(
                        holdingPreparationService.prepareHolding(currentUser, item, totalCurrentValue)
                );
            } catch (RuntimeException ex) {
                String symbol = TradingSymbolNormalizer.normalize(item.getTradingSymbol());
                logger.log(Level.WARNING,
                        "Failed to import holding " + symbol + ": " + ex.getMessage(),
                        ex);
                failedSymbols.add(symbol);
                failedExceptions.add(ex);
            }
        }

        if (!failedSymbols.isEmpty()) {
            IllegalStateException importFailure = new IllegalStateException(
                    "Import aborted; failed holdings: " + String.join(", ", failedSymbols),
                    failedExceptions.getFirst()
            );
            failedExceptions.stream()
                    .skip(1)
                    .forEach(importFailure::addSuppressed);
            throw importFailure;
        }

        return preparedHoldings;
    }

    private boolean hasSupportedTradingSymbol(ZerodhaHoldingItem item) {
        String symbol = item.getTradingSymbol();
        if (symbol == null) {
            logger.warning("Skipping Zerodha holding with null trading symbol");
            return false;
        }

        String normalizedSymbol = TradingSymbolNormalizer.normalize(symbol);
        boolean supported = importableSymbolPattern.matcher(normalizedSymbol).matches();
        if (!supported) {
            logger.info("Skipping Zerodha holding with unsupported symbol: " + symbol);
            return false;
        }
        return true;
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

    private ZerodhaHoldingItem normalizeTradingSymbol(ZerodhaHoldingItem item) {
        ZerodhaHoldingItem normalizedItem = new ZerodhaHoldingItem();
        normalizedItem.setInstrumentToken(item.getInstrumentToken());
        normalizedItem.setTradingSymbol(TradingSymbolNormalizer.normalize(item.getTradingSymbol()));
        normalizedItem.setExchange(item.getExchange());
        normalizedItem.setIsin(item.getIsin());
        normalizedItem.setQuantity(item.getQuantity());
        normalizedItem.setAveragePrice(item.getAveragePrice());
        normalizedItem.setLastPrice(item.getLastPrice());
        normalizedItem.setClosePrice(item.getClosePrice());
        normalizedItem.setPnl(item.getPnl());
        normalizedItem.setDayChange(item.getDayChange());
        normalizedItem.setDayChangePercentage(item.getDayChangePercentage());
        normalizedItem.setProfitLoss(item.getProfitLoss());
        return normalizedItem;
    }
}
