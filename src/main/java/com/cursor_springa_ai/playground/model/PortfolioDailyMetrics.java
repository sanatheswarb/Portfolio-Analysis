package com.cursor_springa_ai.playground.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * End-of-day portfolio aggregate for a single user.
 * One immutable row per (user, date) — never updated once written.
 *
 * <p>The scheduler runs every weekday at 18:20 IST (12:50 UTC). A manual
 * on-demand trigger is also available.
 */
@Entity
@Table(
        name = "portfolio_daily_metrics",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_portfolio_daily_metrics_user_date",
                columnNames = {"user_id", "snapshot_date"}
        )
)
public class PortfolioDailyMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "total_invested", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalInvested;

    @Column(name = "total_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalValue;

    @Column(name = "total_pnl", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalPnl;

    @Column(name = "pnl_percent", precision = 10, scale = 4)
    private BigDecimal pnlPercent;

    /** Weight percentage of the single largest holding — concentration indicator. */
    @Column(name = "largest_weight", precision = 10, scale = 4)
    private BigDecimal largestWeight;

    /** Number of distinct instruments held on this date — diversification indicator. */
    @Column(name = "stock_count", nullable = false)
    private Integer stockCount;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    protected PortfolioDailyMetrics() {
    }

    public PortfolioDailyMetrics(User user, LocalDate snapshotDate,
                                 BigDecimal totalInvested, BigDecimal totalValue,
                                 BigDecimal totalPnl, BigDecimal pnlPercent,
                                 BigDecimal largestWeight, Integer stockCount,
                                 LocalDateTime calculatedAt) {
        this.user = user;
        this.snapshotDate = snapshotDate;
        this.totalInvested = totalInvested;
        this.totalValue = totalValue;
        this.totalPnl = totalPnl;
        this.pnlPercent = pnlPercent;
        this.largestWeight = largestWeight;
        this.stockCount = stockCount;
        this.calculatedAt = calculatedAt;
    }

    public Long getId() { return id; }

    public User getUser() { return user; }

    public LocalDate getSnapshotDate() { return snapshotDate; }

    public BigDecimal getTotalInvested() { return totalInvested; }

    public BigDecimal getTotalValue() { return totalValue; }

    public BigDecimal getTotalPnl() { return totalPnl; }

    public BigDecimal getPnlPercent() { return pnlPercent; }

    public BigDecimal getLargestWeight() { return largestWeight; }

    public Integer getStockCount() { return stockCount; }

    public LocalDateTime getCalculatedAt() { return calculatedAt; }
}
