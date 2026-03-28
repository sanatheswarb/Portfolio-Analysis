package com.cursor_springa_ai.playground.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Aggregated portfolio statistics for a single user, recalculated by a batch job.
 *
 * <p>One row per user — the existing row is overwritten on every batch run.
 * {@code user_id} is both the primary key and a foreign key to {@link User}.
 */
@Entity
@Table(name = "portfolio_stats")
public class PortfolioStats {

    /** Shares the user PK — one summary row per user. */
    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

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

    /** Number of distinct instruments currently held — diversification indicator. */
    @Column(name = "stock_count", nullable = false)
    private Integer stockCount;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    protected PortfolioStats() {
    }

    public PortfolioStats(User user, BigDecimal totalInvested, BigDecimal totalValue,
                          BigDecimal totalPnl, BigDecimal pnlPercent,
                          BigDecimal largestWeight, Integer stockCount,
                          LocalDateTime calculatedAt) {
        this.user = user;
        this.userId = user.getId();
        this.totalInvested = totalInvested;
        this.totalValue = totalValue;
        this.totalPnl = totalPnl;
        this.pnlPercent = pnlPercent;
        this.largestWeight = largestWeight;
        this.stockCount = stockCount;
        this.calculatedAt = calculatedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public BigDecimal getTotalInvested() {
        return totalInvested;
    }

    public void setTotalInvested(BigDecimal totalInvested) {
        this.totalInvested = totalInvested;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public BigDecimal getTotalPnl() {
        return totalPnl;
    }

    public void setTotalPnl(BigDecimal totalPnl) {
        this.totalPnl = totalPnl;
    }

    public BigDecimal getPnlPercent() {
        return pnlPercent;
    }

    public void setPnlPercent(BigDecimal pnlPercent) {
        this.pnlPercent = pnlPercent;
    }

    public BigDecimal getLargestWeight() {
        return largestWeight;
    }

    public void setLargestWeight(BigDecimal largestWeight) {
        this.largestWeight = largestWeight;
    }

    public Integer getStockCount() {
        return stockCount;
    }

    public void setStockCount(Integer stockCount) {
        this.stockCount = stockCount;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(LocalDateTime calculatedAt) {
        this.calculatedAt = calculatedAt;
    }
}
