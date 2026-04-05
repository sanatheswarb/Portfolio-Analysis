package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.model.HoldingSnapshot;
import com.cursor_springa_ai.playground.model.Instrument;
import com.cursor_springa_ai.playground.model.User;
import com.cursor_springa_ai.playground.model.UserHolding;
import com.cursor_springa_ai.playground.repository.HoldingSnapshotRepository;
import com.cursor_springa_ai.playground.repository.UserHoldingRepository;
import com.cursor_springa_ai.playground.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Records end-of-day portfolio snapshots into {@code holding_snapshots}.
 *
 * <p>A snapshot is only written once per (user, instrument, date). Any subsequent call for the
 * same date — whether from the scheduler or the on-demand trigger — is silently skipped.
 *
 * <p>The scheduled job runs every weekday at 16:30 IST (11:00 UTC), shortly after NSE market close.
 * An on-demand snapshot for today can be triggered via {@link #takeSnapshotsForAllUsers()}.
 */
@Service
public class HoldingSnapshotService {

    private static final Logger logger = Logger.getLogger(HoldingSnapshotService.class.getName());

    private final UserRepository userRepository;
    private final UserHoldingRepository userHoldingRepository;
    private final HoldingSnapshotRepository holdingSnapshotRepository;

    public HoldingSnapshotService(UserRepository userRepository,
                                  UserHoldingRepository userHoldingRepository,
                                  HoldingSnapshotRepository holdingSnapshotRepository) {
        this.userRepository = userRepository;
        this.userHoldingRepository = userHoldingRepository;
        this.holdingSnapshotRepository = holdingSnapshotRepository;
    }

    // ------------------------------------------------------------------
    // Scheduler – Mon-Fri 16:30 IST = 11:00 UTC
    // ------------------------------------------------------------------

    /**
     * Daily scheduled snapshot: runs every weekday at 16:30 IST (11:00 UTC) after market close.
     * Snapshots are written only for users whose holdings have already been imported.
     */
    @Scheduled(cron = "0 0 11 * * MON-FRI", zone = "UTC")
    public void scheduledDailySnapshot() {
        logger.info("Scheduled daily snapshot triggered for date: " + LocalDate.now());
        takeSnapshotsForAllUsers();
    }

    // ------------------------------------------------------------------
    // On-demand
    // ------------------------------------------------------------------

    /**
     * Take a snapshot for today for every known user. Safe to call multiple times — rows that
     * already exist for today are silently skipped.
     *
     * @return total number of new snapshot rows written
     */
    @Transactional
    public int takeSnapshotsForAllUsers() {
        return takeSnapshotsForAllUsers(LocalDate.now());
    }

    /**
     * Take a snapshot for a specific date for every known user.
     *
     * @param date the snapshot date
     * @return total number of new snapshot rows written
     */
    @Transactional
    public int takeSnapshotsForAllUsers(LocalDate date) {
        List<User> users = userRepository.findAll();
        int total = 0;
        for (User user : users) {
            total += takeSnapshotForUser(user, date);
        }
        logger.info("Snapshot complete for date " + date + ": " + total + " new row(s) written.");
        return total;
    }

    /**
     * Take a snapshot for a single user on the given date.
     * Holdings that already have a snapshot row for that date are skipped.
     *
     * @return number of new snapshot rows written for this user
     */
    @Transactional
    public int takeSnapshotForUser(User user, LocalDate date) {
        List<UserHolding> holdings = userHoldingRepository.findByUserId(user.getId());
        if (holdings.isEmpty()) {
            return 0;
        }

        // Compute total market value for weight_percent calculation
        BigDecimal totalMarketValue = holdings.stream()
                .map(h -> h.getClosePrice() != null && h.getClosePrice().compareTo(BigDecimal.ZERO) > 0
                        ? BigDecimal.valueOf(h.getQuantity()).multiply(h.getClosePrice())
                        : BigDecimal.valueOf(h.getQuantity()).multiply(Objects.requireNonNullElse(h.getLastPrice(), BigDecimal.ZERO)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int written = 0;
        for (UserHolding holding : holdings) {
            Instrument instrument = holding.getInstrument();
            if (instrument == null) {
                continue;
            }

            // Skip if snapshot already exists for this date
                if (holdingSnapshotRepository.existsByPkUserIdAndPkInstrumentIdAndPkSnapshotDate(
                    user.getId(), instrument.getId(), date)) {
                continue;
            }

            BigDecimal closePrice = holding.getClosePrice() != null
                    && holding.getClosePrice().compareTo(BigDecimal.ZERO) > 0
                    ? holding.getClosePrice()
                    : Objects.requireNonNullElse(holding.getLastPrice(), BigDecimal.ZERO);

            int quantity = holding.getQuantity();
            BigDecimal avgPrice = Objects.requireNonNullElse(holding.getAvgPrice(), BigDecimal.ZERO);
            BigDecimal investedValue = BigDecimal.valueOf(quantity).multiply(avgPrice);
            BigDecimal marketValue = BigDecimal.valueOf(quantity).multiply(closePrice);
            BigDecimal pnl = marketValue.subtract(investedValue);
            BigDecimal weightPercent = totalMarketValue.compareTo(BigDecimal.ZERO) != 0
                    ? marketValue.divide(totalMarketValue, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            holdingSnapshotRepository.save(new HoldingSnapshot(
                    user, instrument, date,
                    quantity, avgPrice, closePrice,
                    investedValue, marketValue, pnl, weightPercent
            ));
            written++;
        }

        return written;
    }
}
