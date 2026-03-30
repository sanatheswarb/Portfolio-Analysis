package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.dto.StockMetrics;
import com.cursor_springa_ai.playground.model.Holding;
import com.cursor_springa_ai.playground.model.Instrument;
import com.cursor_springa_ai.playground.model.StockFundamentals;
import com.cursor_springa_ai.playground.model.UserHolding;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

@Service
public class EnrichedHoldingDataCache {

    private static final Logger logger = Logger.getLogger(EnrichedHoldingDataCache.class.getName());

    private final MarketMetricsCache marketMetricsCache;

    public EnrichedHoldingDataCache(MarketMetricsCache marketMetricsCache) {
        this.marketMetricsCache = marketMetricsCache;
    }

    /**
     * Build enriched holdings on demand from current holdings.
     */
    public List<EnrichedHoldingData> buildEnrichedHoldings(List<Holding> holdings) {
        List<EnrichedHoldingData> enrichedList = new ArrayList<>();

        for (Holding holding : holdings) {
            StockMetrics metrics = marketMetricsCache.getMetrics(holding.getSymbol());
            
            BigDecimal invested = scale(holding.getAverageBuyPrice().multiply(holding.getQuantity()));
            BigDecimal currentValue = scale(holding.getCurrentPrice().multiply(holding.getQuantity()));
            BigDecimal pnl = scale(holding.getProfitLoss());
            BigDecimal currentPrice = scale(holding.getCurrentPrice());
            
            // Calculate profitPercent = pnl / invested * 100
            BigDecimal profitPercent = calculateProfitPercent(pnl, invested);
            
            // Calculate distanceFromHigh = (currentPrice - week52High) / week52High * 100
            BigDecimal distanceFromHigh = calculateDistanceFromHigh(currentPrice, metrics);
            
            // allocationPercent and riskFlags will be calculated during analysis
            BigDecimal allocationPercent = null;
            java.util.List<String> riskFlags = new ArrayList<>();
            
            EnrichedHoldingData enriched = new EnrichedHoldingData(
                    holding.getSymbol(),
                    holding.getAssetType().toString(),
                    scale(holding.getQuantity()),
                    scale(holding.getAverageBuyPrice()),
                    currentPrice,
                    invested,
                    currentValue,
                    pnl,
                    metrics != null ? metrics.sector() : "N/A",
                    metrics != null ? metrics.pe() : null,
                    metrics != null ? metrics.beta() : null,
                    metrics != null ? metrics.sectorPe() : null,
                    metrics != null ? metrics.week52High() : null,
                    metrics != null ? metrics.week52Low() : null,
                    metrics != null ? metrics.marketCapType() : "N/A",
                    metrics != null ? metrics.dma200() : null,
                    allocationPercent,
                    profitPercent,
                    distanceFromHigh,
                    riskFlags
            );
            enrichedList.add(enriched);
        }

        logger.info("Built enriched holdings | Count: " + enrichedList.size());
        return enrichedList;
    }

    /**
     * Build enriched holdings from persisted user holding snapshots.
     */
    public List<EnrichedHoldingData> buildEnrichedHoldingsFromUserHoldings(List<UserHolding> userHoldings) {
        List<EnrichedHoldingData> enrichedList = new ArrayList<>();

        for (UserHolding userHolding : userHoldings) {
            String symbol = userHolding.getSymbol();
            if (symbol == null || symbol.isBlank()) {
                symbol = userHolding.getInstrument() != null ? userHolding.getInstrument().getSymbol() : null;
            }

            StockMetrics metrics = marketMetricsCache.getMetrics(symbol);
            BigDecimal quantity = userHolding.getQuantity() != null
                    ? BigDecimal.valueOf(userHolding.getQuantity())
                    : BigDecimal.ZERO;
            BigDecimal averageBuyPrice = scale(userHolding.getAvgPrice());
            BigDecimal currentPrice = scale(userHolding.getLastPrice());
            BigDecimal invested = scale(userHolding.getInvestedValue());
            BigDecimal currentValue = scale(userHolding.getCurrentValue());
            BigDecimal pnl = scale(userHolding.getPnl());

            BigDecimal profitPercent = calculateProfitPercent(pnl, invested);
            BigDecimal distanceFromHigh = calculateDistanceFromHigh(currentPrice, metrics);
            BigDecimal allocationPercent = null;
            java.util.List<String> riskFlags = new ArrayList<>();

            EnrichedHoldingData enriched = new EnrichedHoldingData(
                    symbol,
                    inferAssetType(symbol),
                    scale(quantity),
                    averageBuyPrice,
                    currentPrice,
                    invested,
                    currentValue,
                    pnl,
                    metrics != null ? metrics.sector() : "N/A",
                    metrics != null ? metrics.pe() : null,
                    metrics != null ? metrics.beta() : null,
                    metrics != null ? metrics.sectorPe() : null,
                    metrics != null ? metrics.week52High() : null,
                    metrics != null ? metrics.week52Low() : null,
                    metrics != null ? metrics.marketCapType() : "N/A",
                    metrics != null ? metrics.dma200() : null,
                    allocationPercent,
                    profitPercent,
                    distanceFromHigh,
                    riskFlags
            );
            enrichedList.add(enriched);
        }

        logger.info("Built enriched holdings from user_holdings | Count: " + enrichedList.size());
        return enrichedList;
    }

    /**
     * Build enriched holdings entirely from the eagerly-fetched JPA graph — no extra DB or API calls.
     * Expects UserHolding → Instrument → StockFundamentals to be JOIN FETCHed.
     */
    public List<EnrichedHoldingData> buildEnrichedHoldingsFromDB(List<UserHolding> userHoldings) {

        List<EnrichedHoldingData> enrichedList = new ArrayList<>();

        for (UserHolding uh : userHoldings) {
            String symbol = uh.getSymbol();
            Instrument instrument = uh.getInstrument();
            if (symbol == null || symbol.isBlank()) {
                symbol = instrument != null ? instrument.getSymbol() : null;
            }

            StockFundamentals sf = instrument != null ? instrument.getStockFundamentals() : null;

            BigDecimal quantity = uh.getQuantity() != null
                    ? BigDecimal.valueOf(uh.getQuantity())
                    : BigDecimal.ZERO;
            BigDecimal currentPrice = scale(uh.getLastPrice());

            // distanceFromHigh from StockFundamentals.week52High
            BigDecimal week52High = sf != null ? sf.getWeek52High() : null;
            BigDecimal distanceFromHigh = calculateDistanceFromHighValue(currentPrice, week52High);

            // Sector & marketCapType: prefer StockFundamentals, fall back to Instrument
            String sector = "N/A";
            if (sf != null && sf.getSector() != null && !sf.getSector().isBlank()) {
                sector = sf.getSector();
            } else if (instrument != null && instrument.getSector() != null && !instrument.getSector().isBlank()) {
                sector = instrument.getSector();
            }

            String marketCapType = "N/A";
            if (instrument != null && instrument.getMarketCapCategory() != null && !instrument.getMarketCapCategory().isBlank()) {
                marketCapType = instrument.getMarketCapCategory();
            }

            EnrichedHoldingData enriched = new EnrichedHoldingData(
                    symbol,
                    inferAssetType(symbol),
                    scale(quantity),
                    scale(uh.getAvgPrice()),
                    currentPrice,
                    scale(uh.getInvestedValue()),
                    scale(uh.getCurrentValue()),
                    scale(uh.getPnl()),
                    sector,
                    sf != null ? sf.getPe() : null,
                    null,  // beta — not available in any DB table
                    sf != null ? sf.getSectorPe() : null,
                    week52High,
                    sf != null ? sf.getWeek52Low() : null,
                    marketCapType,
                    null,  // dma200 — not available in any DB table
                    scale(uh.getWeightPercent()),   // allocationPercent from user_holdings
                    scale(uh.getPnlPercent()),       // profitPercent from user_holdings
                    distanceFromHigh,
                    new ArrayList<>()
            );
            enrichedList.add(enriched);
        }

        logger.info("Built enriched holdings from DB | Count: " + enrichedList.size());
        return enrichedList;
    }

    private BigDecimal calculateDistanceFromHighValue(BigDecimal currentPrice, BigDecimal week52High) {
        if (week52High == null || currentPrice == null
                || week52High.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return currentPrice.subtract(week52High)
                .divide(week52High, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Calculate profit percentage: pnl / invested * 100
     */
    private BigDecimal calculateProfitPercent(BigDecimal pnl, BigDecimal invested) {
        if (invested == null || invested.compareTo(BigDecimal.ZERO) == 0 || pnl == null) {
            return null;
        }
        return pnl.divide(invested, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Calculate distance from 52-week high: (currentPrice - week52High) / week52High * 100
     */
    private BigDecimal calculateDistanceFromHigh(BigDecimal currentPrice, StockMetrics metrics) {
        if (metrics == null || metrics.week52High() == null || currentPrice == null) {
            return null;
        }
        if (metrics.week52High().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return currentPrice.subtract(metrics.week52High())
                .divide(metrics.week52High(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Calculate allocation percent for enriched holdings given portfolio total.
     * allocation % = currentValue / portfolioValue * 100
     * Returns new enriched holdings with allocationPercent calculated.
     */
    public List<EnrichedHoldingData> calculateWithAllocationPercent(List<EnrichedHoldingData> enrichedHoldings, BigDecimal portfolioTotalValue) {
        List<EnrichedHoldingData> result = new ArrayList<>();

        if (portfolioTotalValue == null || portfolioTotalValue.compareTo(BigDecimal.ZERO) == 0) {
            logger.warning("Cannot calculate allocation percent: invalid portfolio value");
            return enrichedHoldings; // Return as-is
        }

        for (EnrichedHoldingData enriched : enrichedHoldings) {
            BigDecimal allocationPercent = null;
            if (enriched.currentValue() != null) {
                allocationPercent = enriched.currentValue()
                        .divide(portfolioTotalValue, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
            }

            // Create new enriched holding with allocationPercent set
            EnrichedHoldingData updated = new EnrichedHoldingData(
                    enriched.symbol(),
                    enriched.assetType(),
                    enriched.quantity(),
                    enriched.averageBuyPrice(),
                    enriched.currentPrice(),
                    enriched.investedValue(),
                    enriched.currentValue(),
                    enriched.profitLoss(),
                    enriched.sector(),
                    enriched.pe(),
                    enriched.beta(),
                    enriched.sectorPe(),
                    enriched.week52High(),
                    enriched.week52Low(),
                    enriched.marketCapType(),
                    enriched.dma200(),
                    allocationPercent,
                    enriched.profitPercent(),
                    enriched.distanceFromHigh(),
                    enriched.riskFlags()
            );
            result.add(updated);
        }

        logger.info("Calculated allocation percent for " + result.size() + " holdings | Portfolio Value: " + portfolioTotalValue);
        return result;
    }

    /**
     * Calculate and apply risk flags for enriched holdings.
     * Returns enriched holdings with populated riskFlags.
     */
    public List<EnrichedHoldingData> calculateRiskFlags(List<EnrichedHoldingData> enrichedHoldings) {
        List<EnrichedHoldingData> result = new ArrayList<>();

        for (EnrichedHoldingData enriched : enrichedHoldings) {
            java.util.List<String> riskFlags = new ArrayList<>();

            // 1. Concentration risk: allocation > 20%
            if (enriched.allocationPercent() != null && 
                enriched.allocationPercent().compareTo(BigDecimal.valueOf(20)) > 0) {
                riskFlags.add("HIGH_CONCENTRATION");
            }

            // 2. Valuation risk: pe > sectorPe * 1.5
            if (enriched.pe() != null && enriched.sectorPe() != null &&
                enriched.pe().compareTo(enriched.sectorPe().multiply(BigDecimal.valueOf(1.5))) > 0) {
                riskFlags.add("HIGH_VALUATION");
            }

            // 3. Drawdown risk: distanceFromHigh < -25
            if (enriched.distanceFromHigh() != null && 
                enriched.distanceFromHigh().compareTo(BigDecimal.valueOf(-25)) < 0) {
                riskFlags.add("DEEP_CORRECTION");
            }

            // 4. Size risk: marketCapType == SMALL
            if (enriched.marketCapType() != null && 
                enriched.marketCapType().equalsIgnoreCase("SMALL")) {
                riskFlags.add("SMALL_CAP_RISK");
            }

            // 5. Profit booking signal: profitPercent > 40
            if (enriched.profitPercent() != null && 
                enriched.profitPercent().compareTo(BigDecimal.valueOf(40)) > 0) {
                riskFlags.add("PROFIT_BOOKING_ZONE");
            }

            // Create new enriched holding with riskFlags set
            EnrichedHoldingData updated = new EnrichedHoldingData(
                    enriched.symbol(),
                    enriched.assetType(),
                    enriched.quantity(),
                    enriched.averageBuyPrice(),
                    enriched.currentPrice(),
                    enriched.investedValue(),
                    enriched.currentValue(),
                    enriched.profitLoss(),
                    enriched.sector(),
                    enriched.pe(),
                    enriched.beta(),
                    enriched.sectorPe(),
                    enriched.week52High(),
                    enriched.week52Low(),
                    enriched.marketCapType(),
                    enriched.dma200(),
                    enriched.allocationPercent(),
                    enriched.profitPercent(),
                    enriched.distanceFromHigh(),
                    riskFlags
            );
            result.add(updated);

        }

        logger.info("Risk flag calculation completed for " + result.size() + " holdings");
        return result;
    }

    /**
     * Scale BigDecimal to 2 decimal places.
     */
    private BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String inferAssetType(String symbol) {
        return symbol != null && symbol.toUpperCase(Locale.ROOT).endsWith("ETF")
                ? "ETF"
                : "STOCK";
    }
}
