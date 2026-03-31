package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.EnrichedHoldingData;
import com.cursor_springa_ai.playground.model.Instrument;
import com.cursor_springa_ai.playground.model.RiskFlag;
import com.cursor_springa_ai.playground.model.StockFundamentals;
import com.cursor_springa_ai.playground.model.UserHolding;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class HoldingAnalyticsService {

    private static final BigDecimal HOLDING_CONCENTRATION_THRESHOLD = BigDecimal.valueOf(20);
    private static final BigDecimal VALUATION_UPPER_MULTIPLIER = BigDecimal.valueOf(1.2);
    private static final BigDecimal VALUATION_LOWER_MULTIPLIER = BigDecimal.valueOf(0.8);
    private static final BigDecimal DEEP_CORRECTION_THRESHOLD = BigDecimal.valueOf(-25);
    private static final BigDecimal PROFIT_BOOKING_THRESHOLD = BigDecimal.valueOf(40);

    public List<EnrichedHoldingData> buildEnrichedHoldings(List<UserHolding> userHoldings) {
        if (userHoldings == null || userHoldings.isEmpty()) {
            return List.of();
        }
        return userHoldings.stream()
                .map(this::buildEnrichedHolding)
                .toList();
    }

    public EnrichedHoldingData buildEnrichedHolding(UserHolding holding) {
        StockFundamentals fundamentals = fundamentalsOf(holding);
        BigDecimal quantity = holding != null && holding.getQuantity() != null
                ? BigDecimal.valueOf(holding.getQuantity())
                : BigDecimal.ZERO;
        BigDecimal currentPrice = scale(holding != null ? holding.getLastPrice() : null);
        BigDecimal week52High = fundamentals != null ? fundamentals.getWeek52High() : null;

        EnrichedHoldingData enrichedHolding = new EnrichedHoldingData(
                resolveSymbol(holding),
                inferAssetType(resolveSector(holding)),
                scale(quantity),
                scale(holding != null ? holding.getAvgPrice() : null),
                currentPrice,
                scale(holding != null ? holding.getInvestedValue() : null),
                scale(holding != null ? holding.getCurrentValue() : null),
                scale(holding != null ? holding.getPnl() : null),
                resolveSector(holding),
                fundamentals != null ? fundamentals.getPe() : null,
                null,
                fundamentals != null ? fundamentals.getSectorPe() : null,
                week52High,
                fundamentals != null ? fundamentals.getWeek52Low() : null,
                resolveMarketCapType(holding),
                null,
                scale(holding != null ? holding.getWeightPercent() : null),
                scale(holding != null ? holding.getPnlPercent() : null),
                calculateDistanceFromHigh(currentPrice, week52High),
                List.of()
        );
        return withRiskFlags(enrichedHolding);
    }

    public List<EnrichedHoldingData> withRiskFlags(List<EnrichedHoldingData> enrichedHoldings) {
        if (enrichedHoldings == null || enrichedHoldings.isEmpty()) {
            return List.of();
        }
        return enrichedHoldings.stream()
                .map(this::withRiskFlags)
                .toList();
    }

    public EnrichedHoldingData withRiskFlags(EnrichedHoldingData enrichedHolding) {
        if (enrichedHolding == null) {
            return null;
        }
        return new EnrichedHoldingData(
                enrichedHolding.symbol(),
                enrichedHolding.assetType(),
                enrichedHolding.quantity(),
                enrichedHolding.averageBuyPrice(),
                enrichedHolding.currentPrice(),
                enrichedHolding.investedValue(),
                enrichedHolding.currentValue(),
                enrichedHolding.profitLoss(),
                enrichedHolding.sector(),
                enrichedHolding.pe(),
                enrichedHolding.beta(),
                enrichedHolding.sectorPe(),
                enrichedHolding.week52High(),
                enrichedHolding.week52Low(),
                enrichedHolding.marketCapType(),
                enrichedHolding.dma200(),
                enrichedHolding.allocationPercent(),
                enrichedHolding.profitPercent(),
                enrichedHolding.distanceFromHigh(),
                calculateRiskFlags(enrichedHolding)
        );
    }

    public List<String> calculateRiskFlags(EnrichedHoldingData enrichedHolding) {
        if (enrichedHolding == null) {
            return List.of();
        }

        List<String> riskFlags = new ArrayList<>();
        if (isGreaterThan(enrichedHolding.allocationPercent(), HOLDING_CONCENTRATION_THRESHOLD)) {
            riskFlags.add(RiskFlag.HIGH_CONCENTRATION.name());
        }
        if ("OVERVALUED".equals(computeValuationFlag(enrichedHolding.pe(), enrichedHolding.sectorPe()))) {
            riskFlags.add(RiskFlag.HIGH_VALUATION.name());
        }
        if (enrichedHolding.distanceFromHigh() != null
                && enrichedHolding.distanceFromHigh().compareTo(DEEP_CORRECTION_THRESHOLD) < 0) {
            riskFlags.add(RiskFlag.DEEP_CORRECTION.name());
        }
        if (enrichedHolding.marketCapType() != null
                && enrichedHolding.marketCapType().equalsIgnoreCase("SMALL")) {
            riskFlags.add(RiskFlag.SMALL_CAP_RISK.name());
        }
        if (isGreaterThan(enrichedHolding.profitPercent(), PROFIT_BOOKING_THRESHOLD)) {
            riskFlags.add(RiskFlag.PROFIT_BOOKING_ZONE.name());
        }
        return List.copyOf(riskFlags);
    }

    public BigDecimal calculateDistanceFromHigh(BigDecimal currentPrice, BigDecimal week52High) {
        if (week52High == null || currentPrice == null || week52High.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return currentPrice.subtract(week52High)
                .divide(week52High, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateVolatility(UserHolding holding) {
        return calculateVolatility(holding != null ? holding.getDayChangePercent() : null);
    }

    public BigDecimal calculateVolatility(BigDecimal dayChangePercent) {
        return dayChangePercent != null ? dayChangePercent.abs() : BigDecimal.ZERO;
    }

    public BigDecimal computeMomentumScore(UserHolding holding) {
        StockFundamentals fundamentals = fundamentalsOf(holding);
        return computeMomentumScore(
                holding != null ? holding.getLastPrice() : null,
                fundamentals != null ? fundamentals.getWeek52High() : null
        );
    }

    public BigDecimal computeMomentumScore(BigDecimal lastPrice, BigDecimal week52High) {
        if (lastPrice == null || week52High == null || week52High.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return lastPrice.divide(week52High, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public String computeValuationFlag(StockFundamentals fundamentals) {
        if (fundamentals == null) {
            return null;
        }
        return computeValuationFlag(fundamentals.getPe(), fundamentals.getSectorPe());
    }

    public String computeValuationFlag(BigDecimal pe, BigDecimal sectorPe) {
        if (pe == null || sectorPe == null || sectorPe.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal upper = sectorPe.multiply(VALUATION_UPPER_MULTIPLIER);
        BigDecimal lower = sectorPe.multiply(VALUATION_LOWER_MULTIPLIER);
        if (pe.compareTo(upper) > 0) {
            return "OVERVALUED";
        }
        if (pe.compareTo(lower) < 0) {
            return "UNDERVALUED";
        }
        return "FAIRLY_VALUED";
    }

    public BigDecimal computeRiskScore(BigDecimal volatility) {
        double v = (volatility != null ? volatility : BigDecimal.ZERO).doubleValue();
        int score;
        if (v <= 0.5) {
            score = 1;
        } else if (v <= 1.0) {
            score = 2;
        } else if (v <= 1.5) {
            score = 3;
        } else if (v <= 2.0) {
            score = 4;
        } else if (v <= 2.5) {
            score = 5;
        } else if (v <= 3.0) {
            score = 6;
        } else if (v <= 4.0) {
            score = 7;
        } else if (v <= 5.0) {
            score = 8;
        } else if (v <= 7.0) {
            score = 9;
        } else {
            score = 10;
        }
        return BigDecimal.valueOf(score);
    }

    public String resolveSector(UserHolding holding) {
        StockFundamentals fundamentals = fundamentalsOf(holding);
        if (fundamentals != null && fundamentals.getSector() != null && !fundamentals.getSector().isBlank()) {
            return fundamentals.getSector();
        }
        Instrument instrument = instrumentOf(holding);
        if (instrument != null && instrument.getSector() != null && !instrument.getSector().isBlank()) {
            return instrument.getSector();
        }
        return "N/A";
    }

    public String resolveMarketCapType(UserHolding holding) {
        Instrument instrument = instrumentOf(holding);
        if (instrument != null && instrument.getMarketCapCategory() != null
                && !instrument.getMarketCapCategory().isBlank()) {
            return instrument.getMarketCapCategory();
        }
        return "N/A";
    }

    private Instrument instrumentOf(UserHolding holding) {
        return holding != null ? holding.getInstrument() : null;
    }

    private StockFundamentals fundamentalsOf(UserHolding holding) {
        Instrument instrument = instrumentOf(holding);
        return instrument != null ? instrument.getStockFundamentals() : null;
    }

    private String resolveSymbol(UserHolding holding) {
        if (holding == null) {
            return null;
        }
        if (holding.getSymbol() != null && !holding.getSymbol().isBlank()) {
            return holding.getSymbol();
        }
        Instrument instrument = instrumentOf(holding);
        return instrument != null ? instrument.getSymbol() : null;
    }

    private BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String inferAssetType(String sector) {
        return sector != null && sector.toUpperCase(Locale.ROOT).endsWith("ETF")
                ? "ETF"
                : "STOCK";
    }

    private boolean isGreaterThan(BigDecimal value, BigDecimal threshold) {
        return value != null && value.compareTo(threshold) > 0;
    }
}

