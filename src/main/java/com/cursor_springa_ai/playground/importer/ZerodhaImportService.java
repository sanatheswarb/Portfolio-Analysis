package com.cursor_springa_ai.playground.importer;
import com.cursor_springa_ai.playground.dto.zerodha.ZerodhaImportResponse;
import com.cursor_springa_ai.playground.integration.zerodha.ZerodhaHoldingsClient;
import com.cursor_springa_ai.playground.dto.zerodha.ZerodhaHoldingItem;
import com.cursor_springa_ai.playground.model.entity.User;
import com.cursor_springa_ai.playground.model.entity.UserHolding;
import com.cursor_springa_ai.playground.service.PortfolioStatsBatchService;
import com.cursor_springa_ai.playground.service.UserHoldingSyncService;
import com.cursor_springa_ai.playground.util.StringNormalizer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates the Zerodha holdings import pipeline.
 *
 * <p>Responsibilities (orchestration only — no financial calculations or persistence logic):
 * <ol>
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
    private final UserHoldingSyncService userHoldingSyncService;
    private final PortfolioStatsBatchService portfolioStatsBatchService;
    private final HoldingPreparationService holdingPreparationService;
    private final HoldingMergeService holdingMergeService;

    public ZerodhaImportService(
            ZerodhaHoldingsClient zerodhaHoldingsClient,
            UserHoldingSyncService userHoldingSyncService,
            PortfolioStatsBatchService portfolioStatsBatchService,
            HoldingPreparationService holdingPreparationService,
            HoldingMergeService holdingMergeService
    ) {
        this.zerodhaHoldingsClient = zerodhaHoldingsClient;
        this.userHoldingSyncService = userHoldingSyncService;
        this.portfolioStatsBatchService = portfolioStatsBatchService;
        this.holdingPreparationService = holdingPreparationService;
        this.holdingMergeService = holdingMergeService;
    }

    public ZerodhaImportResponse importHoldings(User currentUser) {
        List<ZerodhaHoldingItem> fetchedHoldings = zerodhaHoldingsClient.fetchHoldings();
        List<ZerodhaHoldingItem> activeHoldings = fetchedHoldings.stream()
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
                String symbol = StringNormalizer.normalize(item.getTradingSymbol());
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
                    failedExceptions.get(0)
            );
            failedExceptions.stream()
                    .skip(1)
                    .forEach(importFailure::addSuppressed);
            throw importFailure;
        }

        return preparedHoldings;
    }


}
