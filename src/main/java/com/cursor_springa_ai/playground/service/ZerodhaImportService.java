package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.ZerodhaImportResponse;
import com.cursor_springa_ai.playground.integration.zerodha.ZerodhaHoldingsClient;
import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaHoldingItem;
import com.cursor_springa_ai.playground.model.Instrument;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.model.UserHolding;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

@Service
public class ZerodhaImportService {

    private static final Logger logger = Logger.getLogger(ZerodhaImportService.class.getName());

    private final ZerodhaHoldingsClient zerodhaHoldingsClient;
    private final ZerodhaAuthService zerodhaAuthService;
    private final InstrumentEnrichmentService instrumentEnrichmentService;
    private final StockFundamentalsService stockFundamentalsService;
    private final UserHoldingSyncService userHoldingSyncService;
    private final PortfolioStatsBatchService portfolioStatsBatchService;

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
            throw new IllegalStateException("No authenticated Zerodha user found. Please complete login first.");
        }

        List<ZerodhaHoldingItem> incoming = zerodhaHoldingsClient.fetchHoldings();
        List<ZerodhaHoldingItem> activeHoldings = incoming.stream()
                .filter(this::isImportableHolding)
                .toList();

        BigDecimal totalCurrentValue = computeTotalCurrentValue(activeHoldings);

        List<String> importedSymbols = new ArrayList<>();
        List<UserHolding> holdingsToPersist = new ArrayList<>();
        List<String> failedSymbols = new ArrayList<>();
        for (ZerodhaHoldingItem item : activeHoldings) {

            try {
            PreparedHolding preparedHolding = prepareHolding(currentUser, item, totalCurrentValue);
            String symbol = preparedHolding.symbol();
                importedSymbols.add(symbol);
            holdingsToPersist.add(preparedHolding.userHolding());
            } catch (RuntimeException ex) {
                logger.warning("Failed to import holding "
                        + item.getTradingSymbol() + ": " + ex.getMessage());
                failedSymbols.add(item.getTradingSymbol().toUpperCase(Locale.ROOT));
            }
        }

        if (!failedSymbols.isEmpty()) {
            throw new IllegalStateException("Import aborted; failed holdings: " + String.join(", ", failedSymbols));
        }

        userHoldingSyncService.replaceHoldings(currentUser.getId(), holdingsToPersist);
        portfolioStatsBatchService.calculateForUserAsync(currentUser.getId());

        return new ZerodhaImportResponse(
                currentUser.getBrokerUserId(),
                importedSymbols.size(),
                importedSymbols
        );
    }

    // ------------------------------------------------------------------
    // private helpers
    // ------------------------------------------------------------------

    /**
     * Processes a single holding end-to-end: instrument upsert → fundamentals refresh
     * → user holding snapshot.
     *
     * @return the upper-cased trading symbol, or throws on fatal error
     */
    private PreparedHolding prepareHolding(User currentUser, ZerodhaHoldingItem item,
                                           BigDecimal totalCurrentValue) {
        String symbol = item.getTradingSymbol().toUpperCase(Locale.ROOT);
        Instrument instrument = instrumentEnrichmentService.upsertAndEnrich(item);
        if (instrument == null) {
            throw new IllegalStateException("Instrument resolution failed for symbol " + symbol);
        }

        BigDecimal previousClose = stockFundamentalsService.upsertIfStale(instrument);
        UserHolding userHolding = buildUserHolding(currentUser, instrument, item, totalCurrentValue, previousClose);
        return new PreparedHolding(symbol, userHolding);
    }

    private boolean isImportableHolding(ZerodhaHoldingItem item) {
        return item.getTradingSymbol() != null
                && item.getQuantity() != null
                && item.getQuantity().compareTo(BigDecimal.ZERO) > 0;
    }

    private UserHolding buildUserHolding(User user, Instrument instrument, ZerodhaHoldingItem item,
                                         BigDecimal totalCurrentValue,
                                         BigDecimal nsePreviousClose) {
        BigDecimal qty = item.getQuantity();
        // Indian equity markets do not support fractional shares; round to the nearest
        // whole number defensively before storing as Integer.
        int qtyInt = qty.setScale(0, RoundingMode.HALF_UP).intValue();
        BigDecimal avgPrice = nvl(item.getAveragePrice());
        BigDecimal lastPrice = nvl(item.getLastPrice());
        BigDecimal closePrice = resolveClosePrice(item.getClosePrice(), nsePreviousClose);
        String symbol = item.getTradingSymbol().toUpperCase(Locale.ROOT);
        BigDecimal investedValue = qty.multiply(avgPrice);
        BigDecimal currentValue = qty.multiply(lastPrice);
        BigDecimal pnl = nvl(item.getPnl());
        BigDecimal pnlPercent = investedValue.compareTo(BigDecimal.ZERO) != 0
                ? pnl.divide(investedValue, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        BigDecimal dayChange = calculateDayChange(lastPrice, closePrice, item.getDayChange());
        BigDecimal dayChangePct = calculateDayChangePct(dayChange, closePrice, item.getDayChangePercentage());
        BigDecimal weightPercent = totalCurrentValue.compareTo(BigDecimal.ZERO) != 0
                ? currentValue.divide(totalCurrentValue, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        UserHolding userHolding = new UserHolding(
            user, instrument, qtyInt,
            avgPrice, closePrice, lastPrice,
            investedValue, currentValue,
            pnl, pnlPercent,
            dayChange, dayChangePct
        );
        userHolding.setWeightPercent(weightPercent);
        userHolding.setSymbol(symbol);
        return userHolding;
    }

    private BigDecimal computeTotalCurrentValue(List<ZerodhaHoldingItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (ZerodhaHoldingItem item : items) {
            if (item.getQuantity() != null && item.getLastPrice() != null
                    && item.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(item.getQuantity().multiply(item.getLastPrice()));
            }
        }
        return total;
    }

    private BigDecimal resolveClosePrice(BigDecimal kiteClosePrice, BigDecimal nsePreviousClose) {
        if (nsePreviousClose != null && nsePreviousClose.compareTo(BigDecimal.ZERO) > 0) {
            return nsePreviousClose;
        }
        return nvl(kiteClosePrice);
    }

    private BigDecimal calculateDayChange(BigDecimal lastPrice, BigDecimal closePrice, BigDecimal fallbackDayChange) {
        if (closePrice != null && closePrice.compareTo(BigDecimal.ZERO) > 0) {
            return nvl(lastPrice).subtract(closePrice);
        }
        return nvl(fallbackDayChange);
    }

    private BigDecimal calculateDayChangePct(BigDecimal dayChange, BigDecimal closePrice, BigDecimal fallbackPct) {
        if (closePrice != null && closePrice.compareTo(BigDecimal.ZERO) > 0) {
            return dayChange.divide(closePrice, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return nvl(fallbackPct);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private record PreparedHolding(String symbol, UserHolding userHolding) {
    }

}
