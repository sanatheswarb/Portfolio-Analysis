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
    private final HoldingAnalyticsService holdingAnalyticsService;

    public StockMetricsCalculationService(UserHoldingRepository userHoldingRepository,
                                          UserStockMetricsRepository userStockMetricsRepository,
                                          StockFundamentalsRepository stockFundamentalsRepository,
                                          HoldingAnalyticsService holdingAnalyticsService) {
        this.userHoldingRepository = userHoldingRepository;
        this.userStockMetricsRepository = userStockMetricsRepository;
        this.stockFundamentalsRepository = stockFundamentalsRepository;
        this.holdingAnalyticsService = holdingAnalyticsService;
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
        BigDecimal volatility = holdingAnalyticsService.calculateVolatility(holding);
        BigDecimal momentumScore = holdingAnalyticsService.computeMomentumScore(holding.getLastPrice(), week52High);
        String valuationFlag = holdingAnalyticsService.computeValuationFlag(fundamentals);
        BigDecimal riskScore = holdingAnalyticsService.computeRiskScore(volatility);

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

}
