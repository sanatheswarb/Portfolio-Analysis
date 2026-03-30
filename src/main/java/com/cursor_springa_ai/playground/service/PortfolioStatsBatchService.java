package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.model.PortfolioStats;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.model.UserHolding;
import com.cursor_springa_ai.playground.repository.PortfolioStatsRepository;
import com.cursor_springa_ai.playground.repository.UserHoldingRepository;
import com.cursor_springa_ai.playground.repository.UserRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Batch job that computes aggregated portfolio statistics per user and persists them in
 * {@code portfolio_stats}.
 *
 * <p>One row per user — the row is upserted (created or fully overwritten) on every run.
 *
 * <p>Metrics are derived from {@code user_holdings} (the live import snapshot):
 * <ul>
 *   <li>{@code total_invested}  = Σ(quantity × avg_price)</li>
 *   <li>{@code total_value}     = Σ(current_value)</li>
 *   <li>{@code total_pnl}       = total_value − total_invested</li>
 *   <li>{@code pnl_percent}     = total_pnl / total_invested × 100</li>
 *   <li>{@code largest_weight}  = MAX(weight_percent) — concentration indicator</li>
 *   <li>{@code stock_count}     = number of distinct holdings — diversification indicator</li>
 * </ul>
 *
 * <p>The scheduler runs every weekday at 17:00 IST (11:30 UTC), after the daily holding snapshot.
 * It can also be triggered on-demand via {@link #calculateForAllUsers()}.
 */
@Service
public class PortfolioStatsBatchService {

    private static final Logger logger = Logger.getLogger(PortfolioStatsBatchService.class.getName());

    private final UserRepository userRepository;
    private final UserHoldingRepository userHoldingRepository;
    private final PortfolioStatsRepository portfolioStatsRepository;

    public PortfolioStatsBatchService(UserRepository userRepository,
                                      UserHoldingRepository userHoldingRepository,
                                      PortfolioStatsRepository portfolioStatsRepository) {
        this.userRepository = userRepository;
        this.userHoldingRepository = userHoldingRepository;
        this.portfolioStatsRepository = portfolioStatsRepository;
    }

    // ------------------------------------------------------------------
    // Scheduler – Mon-Fri 17:00 IST = 11:30 UTC (after daily snapshot)
    // ------------------------------------------------------------------

    @Scheduled(cron = "0 30 11 * * MON-FRI", zone = "UTC")
    public void scheduledCalculation() {
        logger.info("Scheduled portfolio stats calculation triggered at: " + LocalDateTime.now());
        calculateForAllUsers();
    }

    // ------------------------------------------------------------------
    // On-demand
    // ------------------------------------------------------------------

    /**
     * Recalculate and persist portfolio statistics for every known user.
     *
     * @return number of user rows written
     */
    @Transactional
    public int calculateForAllUsers() {
        List<User> users = userRepository.findAll();
        int count = 0;
        for (User user : users) {
            calculateForUser(user);
            count++;
        }
        logger.info("Portfolio stats batch complete: " + count + " user(s) processed.");
        return count;
    }

    /**
     * Fire-and-forget variant — runs {@link #calculateForUser} on a separate thread.
     * Accepts a plain user ID to avoid passing a detached Hibernate entity across threads.
     */
    @Async
    @Transactional
    public void calculateForUserAsync(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            logger.warning("User not found for async stats calculation: userId=" + userId);
            return;
        }
        calculateForUser(user);
    }

   
    public void calculateForUser(User user) {
        List<UserHolding> holdings = userHoldingRepository.findByUserId(user.getId());

        if (holdings.isEmpty()) {
            logger.fine("No holdings found for user=" + user.getId() + " — skipping stats calculation.");
            return;
        }

        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal largestWeight = BigDecimal.ZERO;
        BigDecimal dayChange = BigDecimal.ZERO;
        BigDecimal sumWeightSquared = BigDecimal.ZERO;

        for (UserHolding h : holdings) {
            totalInvested = totalInvested.add(nvl(h.getInvestedValue()));
            totalValue = totalValue.add(nvl(h.getCurrentValue()));
            dayChange = dayChange.add(nvl(h.getDayChange()));
            BigDecimal w = nvl(h.getWeightPercent());
            if (w.compareTo(largestWeight) > 0) {
                largestWeight = w;
            }
            // weight as fraction (e.g. 0.25 for 25%)
            BigDecimal wFraction = w.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            sumWeightSquared = sumWeightSquared.add(wFraction.multiply(wFraction));
        }

        BigDecimal totalPnl = totalValue.subtract(totalInvested);
        BigDecimal pnlPercent = totalInvested.compareTo(BigDecimal.ZERO) != 0
                ? totalPnl.divide(totalInvested, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // day_change_percent = day_change / (portfolio_value - day_change) * 100
        BigDecimal previousValue = totalValue.subtract(dayChange);
        BigDecimal dayChangePercent = previousValue.compareTo(BigDecimal.ZERO) != 0
                ? dayChange.divide(previousValue, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // Top 3 holdings by weight
        BigDecimal top3HoldingPercent = holdings.stream()
                .map(h -> nvl(h.getWeightPercent()))
                .sorted(Comparator.reverseOrder())
                .limit(3)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Diversification score = 1 - HHI (Herfindahl index)
        BigDecimal diversificationScore = BigDecimal.ONE.subtract(sumWeightSquared)
                .setScale(4, RoundingMode.HALF_UP);

        int stockCount = holdings.size();

        LocalDateTime now = LocalDateTime.now();

        PortfolioStats portfolioStats = new PortfolioStats(
            user.getId(),
            totalInvested,
            totalValue,
            totalPnl,
            pnlPercent,
            largestWeight,
            stockCount,
            dayChange,
            dayChangePercent,
            top3HoldingPercent,
            diversificationScore,
            now
        );

        portfolioStatsRepository.save(portfolioStats);

        logger.info("Portfolio stats saved for user=" + user.getId()
                + " totalInvested=" + totalInvested
                + " totalValue=" + totalValue
                + " pnl=" + totalPnl
                + " dayChange=" + dayChange
                + " stocks=" + stockCount);
    }

    // ------------------------------------------------------------------
    // private helpers
    // ------------------------------------------------------------------

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
