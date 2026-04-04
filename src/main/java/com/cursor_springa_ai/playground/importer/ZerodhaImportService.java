package com.cursor_springa_ai.playground.importer;

import com.cursor_springa_ai.playground.dto.ZerodhaImportResponse;
import com.cursor_springa_ai.playground.integration.zerodha.ZerodhaHoldingsClient;
import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaHoldingItem;
import com.cursor_springa_ai.playground.model.Instrument;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.model.UserHolding;
import com.cursor_springa_ai.playground.service.InstrumentEnrichmentService;
import com.cursor_springa_ai.playground.service.PortfolioStatsBatchService;
import com.cursor_springa_ai.playground.service.StockFundamentalsService;
import com.cursor_springa_ai.playground.service.UserHoldingSyncService;
import com.cursor_springa_ai.playground.service.ZerodhaAuthService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * <p>Financial calculations are delegated to {@link HoldingValueCalculator}.
 * Persistence is delegated to {@link UserHoldingSyncService}.
 */
@Service
public class ZerodhaImportService {

    private static final Logger logger = Logger.getLogger(ZerodhaImportService.class.getName());

    private final ZerodhaHoldingsClient zerodhaHoldingsClient;
    private final ZerodhaAuthService zerodhaAuthService;
    private final InstrumentEnrichmentService instrumentEnrichmentService;
    private final StockFundamentalsService stockFundamentalsService;
    private final UserHoldingSyncService userHoldingSyncService;
    private final PortfolioStatsBatchService portfolioStatsBatchService;
    private final HoldingValueCalculator holdingValueCalculator = new HoldingValueCalculator();

    public ZerodhaImportService(
            ZerodhaHoldingsClient zerodhaHoldingsClient,
            ZerodhaAuthService zerodhaAuthService,
            InstrumentEnrichmentService instrumentEnrichmentService,
            StockFundamentalsService stockFundamentalsService,
            UserHoldingSyncService userHoldingSyncService,
            PortfolioStatsBatchService portfolioStatsBatchService
    ) {
        this.zerodhaHoldingsClient = zerodhaHoldingsClient;
        this.zerodhaAuthService = zerodhaAuthService;
        this.instrumentEnrichmentService = instrumentEnrichmentService;
        this.stockFundamentalsService = stockFundamentalsService;
        this.userHoldingSyncService = userHoldingSyncService;
        this.portfolioStatsBatchService = portfolioStatsBatchService;
    }

    public ZerodhaImportResponse importHoldings() {
        User currentUser = zerodhaAuthService.getCurrentUser();
        if (currentUser == null) {
            throw new IllegalStateException(
                    "No authenticated Zerodha user found. Please complete login first.");
        }

        List<ZerodhaHoldingItem> incoming = zerodhaHoldingsClient.fetchHoldings();
        BigDecimal totalCurrentValue = holdingValueCalculator.computeTotalCurrentValue(incoming);

        List<UserHolding> holdings = new ArrayList<>();
        List<String> failedSymbols = new ArrayList<>();
        RuntimeException lastFailure = null;

        for (ZerodhaHoldingItem item : incoming) {
            if (item.getTradingSymbol() == null
                    || item.getQuantity() == null
                    || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String symbol = item.getTradingSymbol().toUpperCase(Locale.ROOT);
            try {
                Instrument instrument = instrumentEnrichmentService.upsertAndEnrich(item);
                if (instrument != null) {
                    BigDecimal previousClose = stockFundamentalsService.upsertIfStale(instrument);
                    HoldingComputedValues values = holdingValueCalculator.computeValues(
                            item, totalCurrentValue, previousClose);
                    holdings.add(buildUserHolding(currentUser, instrument, values));
                }
            } catch (RuntimeException ex) {
                logger.warning("Failed to prepare holding " + symbol + ": " + ex.getMessage());
                failedSymbols.add(symbol);
                lastFailure = ex;
            }
        }

        if (!failedSymbols.isEmpty()) {
            throw new IllegalStateException(
                    "Import aborted; failed holdings: " + String.join(", ", failedSymbols),
                    lastFailure);
        }

        userHoldingSyncService.replaceHoldings(currentUser.getId(), holdings);
        portfolioStatsBatchService.calculateForUserAsync(currentUser.getId());

        List<String> importedSymbols = holdings.stream()
                .map(UserHolding::getSymbol)
                .toList();

        return new ZerodhaImportResponse(
                currentUser.getBrokerUserId(),
                importedSymbols.size(),
                importedSymbols
        );
    }

    // ------------------------------------------------------------------
    // private helpers
    // ------------------------------------------------------------------

    private UserHolding buildUserHolding(User user, Instrument instrument, HoldingComputedValues v) {
        UserHolding holding = new UserHolding(
                user, instrument, v.quantity(),
                v.avgPrice(), v.closePrice(), v.lastPrice(),
                v.investedValue(), v.currentValue(),
                v.pnl(), v.pnlPercent(),
                v.dayChange(), v.dayChangePct()
        );
        holding.setWeightPercent(v.weightPercent());
        holding.setSymbol(v.symbol());
        return holding;
    }
}

