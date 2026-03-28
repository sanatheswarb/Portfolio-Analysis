package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.ZerodhaImportResponse;
import com.cursor_springa_ai.playground.integration.zerodha.ZerodhaHoldingsClient;
import com.cursor_springa_ai.playground.integration.zerodha.dto.ZerodhaHoldingItem;
import com.cursor_springa_ai.playground.model.AssetType;
import com.cursor_springa_ai.playground.model.Holding;
import com.cursor_springa_ai.playground.model.Instrument;
import com.cursor_springa_ai.playground.model.Portfolio;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.model.UserHolding;
import com.cursor_springa_ai.playground.repository.UserHoldingRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ZerodhaImportService {

    private final ZerodhaHoldingsClient zerodhaHoldingsClient;
    private final PortfolioService portfolioService;
    private final MarketMetricsCache marketMetricsCache;
    private final EnrichedHoldingDataCache enrichedHoldingDataCache;
    private final ZerodhaAuthService zerodhaAuthService;
    private final InstrumentEnrichmentService instrumentEnrichmentService;
    private final UserHoldingRepository userHoldingRepository;

    public ZerodhaImportService(
            ZerodhaHoldingsClient zerodhaHoldingsClient,
            PortfolioService portfolioService,
            MarketMetricsCache marketMetricsCache,
            EnrichedHoldingDataCache enrichedHoldingDataCache,
            ZerodhaAuthService zerodhaAuthService,
            InstrumentEnrichmentService instrumentEnrichmentService,
            UserHoldingRepository userHoldingRepository
    ) {
        this.zerodhaHoldingsClient = zerodhaHoldingsClient;
        this.portfolioService = portfolioService;
        this.marketMetricsCache = marketMetricsCache;
        this.enrichedHoldingDataCache = enrichedHoldingDataCache;
        this.zerodhaAuthService = zerodhaAuthService;
        this.instrumentEnrichmentService = instrumentEnrichmentService;
        this.userHoldingRepository = userHoldingRepository;
    }

    public ZerodhaImportResponse importHoldings(String portfolioId) {
        return importHoldingsWithAutoCreate(portfolioId, null);
    }

    public ZerodhaImportResponse importHoldingsWithAutoCreate(String portfolioId, String ownerName) {
        String resolvedPortfolioId = resolvePortfolioId(portfolioId, ownerName);
        User currentUser = zerodhaAuthService.getCurrentUser();

        List<ZerodhaHoldingItem> incoming = zerodhaHoldingsClient.fetchHoldings();
        List<String> importedSymbols = new ArrayList<>();
        List<Holding> holdingsToEnrich = new ArrayList<>();

        // Batch fetch and cache market metrics for all holdings at once
        marketMetricsCache.batchFetchAndCache(incoming);

        // Compute total current value across all valid items for weight calculation
        BigDecimal totalCurrentValue = computeTotalCurrentValue(incoming);

        for (ZerodhaHoldingItem item : incoming) {
            if (item.getTradingSymbol() == null
                    || item.getQuantity() == null
                    || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String symbol = item.getTradingSymbol().toUpperCase(Locale.ROOT);
            String exchange = item.getExchange() != null ? item.getExchange() : "NSE";

            // Upsert instruments catalogue (insert if new, enrich from NSE if not yet enriched)
            Instrument instrument = instrumentEnrichmentService.upsertAndEnrich(item);

            Holding holding = new Holding(
                    symbol,
                    exchange,
                    resolveAssetType(item),
                    item.getQuantity(),
                    item.getAveragePrice() == null ? BigDecimal.ZERO : item.getAveragePrice(),
                    item.getLastPrice() == null ? BigDecimal.ZERO : item.getLastPrice(),
                    item.getPnl() == null ? BigDecimal.ZERO : item.getPnl()
            );

            portfolioService.addOrUpdateHolding(resolvedPortfolioId, holding);
            importedSymbols.add(holding.getSymbol());
            holdingsToEnrich.add(holding);

            // Persist user_holdings snapshot if user is authenticated
            if (currentUser != null && instrument != null) {
                upsertUserHolding(currentUser, instrument, item, totalCurrentValue);
            }
        }

        // Build and cache enriched holdings for the portfolio
        enrichedHoldingDataCache.buildAndCache(resolvedPortfolioId, holdingsToEnrich);

        return new ZerodhaImportResponse(
                resolvedPortfolioId,
                importedSymbols.size(),
                importedSymbols
        );
    }

    // ------------------------------------------------------------------
    // private helpers
    // ------------------------------------------------------------------

    private void upsertUserHolding(User user, Instrument instrument, ZerodhaHoldingItem item,
                                   BigDecimal totalCurrentValue) {
        BigDecimal qty = item.getQuantity();
        BigDecimal avgPrice = nvl(item.getAveragePrice());
        BigDecimal closePrice = nvl(item.getClosePrice());
        BigDecimal lastPrice = nvl(item.getLastPrice());

        BigDecimal investedValue = qty.multiply(avgPrice);
        BigDecimal currentValue = qty.multiply(lastPrice);
        BigDecimal pnl = nvl(item.getPnl());
        BigDecimal pnlPercent = investedValue.compareTo(BigDecimal.ZERO) != 0
                ? pnl.divide(investedValue, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        BigDecimal dayChange = nvl(item.getDayChange());
        BigDecimal dayChangePct = nvl(item.getDayChangePercentage());
        BigDecimal weightPercent = totalCurrentValue.compareTo(BigDecimal.ZERO) != 0
                ? currentValue.divide(totalCurrentValue, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        userHoldingRepository
                .findByUserIdAndInstrumentInstrumentToken(user.getId(), instrument.getInstrumentToken())
                .ifPresentOrElse(
                        existing -> {
                            existing.setQuantity(qty.intValue());
                            existing.setAvgPrice(avgPrice);
                            existing.setClosePrice(closePrice);
                            existing.setLastPrice(lastPrice);
                            existing.setInvestedValue(investedValue);
                            existing.setCurrentValue(currentValue);
                            existing.setPnl(pnl);
                            existing.setPnlPercent(pnlPercent);
                            existing.setDayChange(dayChange);
                            existing.setDayChangePercent(dayChangePct);
                            existing.setWeightPercent(weightPercent);
                            userHoldingRepository.save(existing);
                        },
                        () -> {
                            UserHolding newHolding = new UserHolding(
                                    user, instrument, qty.intValue(),
                                    avgPrice, closePrice, lastPrice,
                                    investedValue, currentValue,
                                    pnl, pnlPercent,
                                    dayChange, dayChangePct
                            );
                            newHolding.setWeightPercent(weightPercent);
                            userHoldingRepository.save(newHolding);
                        }
                );
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

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String resolvePortfolioId(String portfolioId, String ownerName) {
        if (StringUtils.hasText(portfolioId)) {
            return portfolioId;
        }
        if (!StringUtils.hasText(ownerName)) {
            throw new IllegalArgumentException("ownerName is required when portfolioId is not provided");
        }
        Portfolio created = portfolioService.createPortfolio(ownerName.trim());
        return created.getId();
    }

    private AssetType resolveAssetType(ZerodhaHoldingItem item) {
        String symbol = item.getTradingSymbol() == null ? "" : item.getTradingSymbol().toUpperCase(Locale.ROOT);
        if (symbol.endsWith("ETF")) {
            return AssetType.ETF;
        }
        return AssetType.STOCK;
    }
}
