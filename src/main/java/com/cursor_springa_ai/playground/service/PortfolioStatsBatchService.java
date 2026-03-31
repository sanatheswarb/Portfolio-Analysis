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

import java.time.LocalDateTime;
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
    private final PortfolioAnalyticsService portfolioAnalyticsService;

    public PortfolioStatsBatchService(UserRepository userRepository,
                                      UserHoldingRepository userHoldingRepository,
                                      PortfolioStatsRepository portfolioStatsRepository,
                                      PortfolioAnalyticsService portfolioAnalyticsService) {
        this.userRepository = userRepository;
        this.userHoldingRepository = userHoldingRepository;
        this.portfolioStatsRepository = portfolioStatsRepository;
        this.portfolioAnalyticsService = portfolioAnalyticsService;
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

        PortfolioStats portfolioStats = portfolioAnalyticsService
                .calculatePortfolioStats(user, holdings, LocalDateTime.now());

        portfolioStatsRepository.save(portfolioStats);

        logger.info("Portfolio stats saved for user=" + user.getId()
                + " totalInvested=" + portfolioStats.getTotalInvested()
                + " totalValue=" + portfolioStats.getTotalValue()
                + " pnl=" + portfolioStats.getTotalPnl()
                + " dayChange=" + portfolioStats.getDayChange()
                + " stocks=" + portfolioStats.getStockCount());
    }
}
