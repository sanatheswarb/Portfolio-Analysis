package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.model.Instrument;
import com.cursor_springa_ai.playground.model.StockFundamentals;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.model.UserHolding;
import com.cursor_springa_ai.playground.model.UserStockMetrics;
import com.cursor_springa_ai.playground.repository.StockFundamentalsRepository;
import com.cursor_springa_ai.playground.repository.UserHoldingRepository;
import com.cursor_springa_ai.playground.repository.UserStockMetricsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * Calculates and persists per-user, per-instrument market metrics after every
 * holdings import.
 *
 * <h3>Derived fields</h3>
 * <ul>
 *   <li><b>valuation_flag</b> — OVERVALUED / FAIRLY_VALUED / UNDERVALUED
 *       (stock PE vs sector PE with ±20 % bands)</li>
 *   <li><b>momentum_score</b> — (lastPrice / week52High) × 100; 100 = at 52-week high</li>
 *   <li><b>volatility</b> — |dayChangePercent| from the last import</li>
 *   <li><b>risk_score</b> — 1–10 scale derived from volatility</li>
 *   <li><b>weight_percent</b> — from user_holdings</li>
 *   <li><b>week52_high</b> — from the NSE market-metrics cache</li>
 * </ul>
 */
@Service
public class StockMetricsCalculationService {

    private static final Logger logger = Logger.getLogger(StockMetricsCalculationService.class.getName());

    private final UserHoldingRepository userHoldingRepository;
    private final UserStockMetricsRepository userStockMetricsRepository;
    private final StockFundamentalsRepository stockFundamentalsRepository;

    public StockMetricsCalculationService(UserHoldingRepository userHoldingRepository,
                                          UserStockMetricsRepository userStockMetricsRepository,
                                          StockFundamentalsRepository stockFundamentalsRepository) {
        this.userHoldingRepository = userHoldingRepository;
        this.userStockMetricsRepository = userStockMetricsRepository;
        this.stockFundamentalsRepository = stockFundamentalsRepository;
    }

    /**
     * Recalculate metrics for every holding of the given user and upsert rows.
     * Called at the end of each holdings import.
     */
    @Transactional
    public void calculateForUser(User user) {
        List<UserHolding> holdings = userHoldingRepository.findByUserId(user.getId());
        if (holdings.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (UserHolding holding : holdings) {
            upsertMetrics(user, holding, now);
        }
        logger.info("UserStockMetrics recalculated for user=" + user.getId()
                + " holdings=" + holdings.size());
    }

    // ------------------------------------------------------------------
    // private helpers
    // ------------------------------------------------------------------

    private void upsertMetrics(User user, UserHolding holding, LocalDateTime calculatedAt) {
        Instrument instrument = holding.getInstrument();
        if (instrument == null) {
            return;
        }

        StockFundamentals fundamentals = stockFundamentalsRepository
                .findById(instrument.getInstrumentToken()).orElse(null);

        BigDecimal week52High = fundamentals != null ? fundamentals.getWeek52High() : null;
        BigDecimal volatility = holding.getDayChangePercent() != null
                ? holding.getDayChangePercent().abs()
                : BigDecimal.ZERO;
        BigDecimal momentumScore = computeMomentumScore(nvl(holding.getLastPrice()), week52High);
        String valuationFlag = computeValuationFlag(fundamentals);
        BigDecimal riskScore = computeRiskScore(volatility);

        userStockMetricsRepository
                .findByUserIdAndInstrumentInstrumentToken(user.getId(), instrument.getInstrumentToken())
                .ifPresentOrElse(
                        existing -> {
                            applyFields(existing, valuationFlag, riskScore, momentumScore,
                                    volatility, holding.getWeightPercent(), week52High, calculatedAt);
                            userStockMetricsRepository.save(existing);
                        },
                        () -> {
                            UserStockMetrics m = new UserStockMetrics(user, instrument);
                            applyFields(m, valuationFlag, riskScore, momentumScore,
                                    volatility, holding.getWeightPercent(), week52High, calculatedAt);
                            userStockMetricsRepository.save(m);
                        });
    }

    private void applyFields(UserStockMetrics m, String valuationFlag, BigDecimal riskScore,
                              BigDecimal momentumScore, BigDecimal volatility,
                              BigDecimal weightPercent, BigDecimal week52High,
                              LocalDateTime calculatedAt) {
        m.setValuationFlag(valuationFlag);
        m.setRiskScore(riskScore);
        m.setMomentumScore(momentumScore);
        m.setVolatility(volatility);
        m.setWeightPercent(weightPercent);
        m.setWeek52High(week52High);
        m.setCalculatedAt(calculatedAt);
    }

    /**
     * Momentum = (lastPrice / week52High) × 100.
     * Returns null when week52High is unavailable or zero.
     */
    private BigDecimal computeMomentumScore(BigDecimal lastPrice, BigDecimal week52High) {
        if (week52High == null || week52High.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return lastPrice.divide(week52High, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Valuation flag based on stock PE vs sector PE (±20 % bands).
     * Returns null when PE data is unavailable.
     */
    private String computeValuationFlag(StockFundamentals fundamentals) {
        if (fundamentals == null || fundamentals.getPe() == null || fundamentals.getSectorPe() == null
                || fundamentals.getSectorPe().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal pe = fundamentals.getPe();
        BigDecimal sectorPe = fundamentals.getSectorPe();
        BigDecimal upper = sectorPe.multiply(BigDecimal.valueOf(1.2));
        BigDecimal lower = sectorPe.multiply(BigDecimal.valueOf(0.8));
        if (pe.compareTo(upper) > 0) {
            return "OVERVALUED";
        }
        if (pe.compareTo(lower) < 0) {
            return "UNDERVALUED";
        }
        return "FAIRLY_VALUED";
    }

    /**
     * Risk score 1–10 based on absolute daily-change percentage.
     */
    private BigDecimal computeRiskScore(BigDecimal volatility) {
        double v = volatility.doubleValue();
        int score;
        if (v <= 0.5)      score = 1;
        else if (v <= 1.0) score = 2;
        else if (v <= 1.5) score = 3;
        else if (v <= 2.0) score = 4;
        else if (v <= 2.5) score = 5;
        else if (v <= 3.0) score = 6;
        else if (v <= 4.0) score = 7;
        else if (v <= 5.0) score = 8;
        else if (v <= 7.0) score = 9;
        else               score = 10;
        return BigDecimal.valueOf(score);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
