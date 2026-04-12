package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.model.entity.PortfolioDailyMetrics;
import com.cursor_springa_ai.playground.model.entity.User;
import com.cursor_springa_ai.playground.model.entity.UserHolding;
import com.cursor_springa_ai.playground.repository.PortfolioDailyMetricsRepository;
import com.cursor_springa_ai.playground.repository.UserHoldingRepository;
import com.cursor_springa_ai.playground.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Writes one end-of-day portfolio aggregate row per (user, date) into
 * {@code portfolio_daily_metrics}.
 *
 * <p>The row is <em>never</em> updated after insertion — repeated calls for the same date
 * are silently skipped (insert-if-absent).
 *
 * <p>The scheduled job fires every weekday at 18:20 IST (12:50 UTC — IST is UTC+05:30),
 * NSE market close and after the holding-snapshot job. An on-demand trigger is also
 * available via {@link #calculateForAllUsers()}.
 */
@Service
public class PortfolioDailyMetricsService {

    private static final Logger logger = Logger.getLogger(PortfolioDailyMetricsService.class.getName());

    private final UserRepository userRepository;
    private final UserHoldingRepository userHoldingRepository;
    private final PortfolioDailyMetricsRepository dailyMetricsRepository;

    public PortfolioDailyMetricsService(UserRepository userRepository,
                                        UserHoldingRepository userHoldingRepository,
                                        PortfolioDailyMetricsRepository dailyMetricsRepository) {
        this.userRepository = userRepository;
        this.userHoldingRepository = userHoldingRepository;
        this.dailyMetricsRepository = dailyMetricsRepository;
    }

    // ------------------------------------------------------------------
    // Scheduler – Mon-Fri 18:20 IST = 12:50 UTC
    // ------------------------------------------------------------------

    @Scheduled(cron = "0 50 12 * * MON-FRI", zone = "UTC")
    public void scheduledDailyMetrics() {
        logger.info("Scheduled portfolio_daily_metrics triggered for date: " + LocalDate.now());
        calculateForAllUsers();
    }

    // ------------------------------------------------------------------
    // On-demand
    // ------------------------------------------------------------------

    /**
     * Calculate and persist today's metrics for every known user.
     * Already-written rows for today are skipped.
     *
     * @return number of new rows written
     */
    @Transactional
    public int calculateForAllUsers() {
        return calculateForAllUsers(LocalDate.now());
    }

    /**
     * Calculate and persist metrics for the given date for every known user.
     *
     * @return number of new rows written
     */
    @Transactional
    public int calculateForAllUsers(LocalDate date) {
        List<User> users = userRepository.findAll();
        int total = 0;
        for (User user : users) {
            if (calculateForUser(user, date)) {
                total++;
            }
        }
        logger.info("portfolio_daily_metrics: " + total + " new row(s) written for date=" + date);
        return total;
    }

    /**
     * Calculate and persist daily metrics for one user on the given date.
     * Returns {@code false} (and skips) if a row already exists for that date.
     */
    @Transactional
    public boolean calculateForUser(User user, LocalDate date) {
        if (dailyMetricsRepository.existsByUserIdAndSnapshotDate(user.getId(), date)) {
            logger.fine("portfolio_daily_metrics already exists for user=" + user.getId()
                    + " date=" + date + " — skipping.");
            return false;
        }

        List<UserHolding> holdings = userHoldingRepository.findByUserId(user.getId());
        if (holdings.isEmpty()) {
            return false;
        }

        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal largestWeight = BigDecimal.ZERO;

        for (UserHolding h : holdings) {
            totalInvested = totalInvested.add(Objects.requireNonNullElse(h.getInvestedValue(), BigDecimal.ZERO));
            totalValue = totalValue.add(Objects.requireNonNullElse(h.getCurrentValue(), BigDecimal.ZERO));
            BigDecimal w = Objects.requireNonNullElse(h.getWeightPercent(), BigDecimal.ZERO);
            if (w.compareTo(largestWeight) > 0) {
                largestWeight = w;
            }
        }

        BigDecimal totalPnl = totalValue.subtract(totalInvested);
        BigDecimal pnlPercent = totalInvested.compareTo(BigDecimal.ZERO) != 0
                ? totalPnl.divide(totalInvested, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        dailyMetricsRepository.save(new PortfolioDailyMetrics(
                user, date,
                totalInvested, totalValue, totalPnl, pnlPercent,
                largestWeight, holdings.size(),
                LocalDateTime.now()
        ));

        logger.info("portfolio_daily_metrics saved: user=" + user.getId() + " date=" + date
                + " totalValue=" + totalValue + " pnl=" + totalPnl);
        return true;
    }
}
