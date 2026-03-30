package com.cursor_springa_ai.playground.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
    @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
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

    /** Sum of day_change across all user holdings. */
    @Column(name = "day_change", precision = 18, scale = 4)
    private BigDecimal dayChange;

    /** day_change / (total_value − day_change) × 100. */
    @Column(name = "day_change_percent", precision = 10, scale = 4)
    private BigDecimal dayChangePercent;

    /** Sum of weight_percent for the top 3 holdings — concentration indicator. */
    @Column(name = "top3_holding_percent", precision = 10, scale = 4)
    private BigDecimal top3HoldingPercent;

    /** 1 − Σ(weight_i²) — Herfindahl-based diversification score (0 = concentrated, 1 = diversified). */
    @Column(name = "diversification_score", precision = 10, scale = 4)
    private BigDecimal diversificationScore;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    protected PortfolioStats() {
    }

    public PortfolioStats(Long userId, BigDecimal totalInvested, BigDecimal totalValue,
                          BigDecimal totalPnl, BigDecimal pnlPercent,
                          BigDecimal largestWeight, Integer stockCount,
                          BigDecimal dayChange, BigDecimal dayChangePercent,
                          BigDecimal top3HoldingPercent, BigDecimal diversificationScore,
                          LocalDateTime calculatedAt) {
        this.userId = userId;
        this.totalInvested = totalInvested;
        this.totalValue = totalValue;
        this.totalPnl = totalPnl;
        this.pnlPercent = pnlPercent;
        this.largestWeight = largestWeight;
        this.stockCount = stockCount;
        this.dayChange = dayChange;
        this.dayChangePercent = dayChangePercent;
        this.top3HoldingPercent = top3HoldingPercent;
        this.diversificationScore = diversificationScore;
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

    public BigDecimal getDayChange() {
        return dayChange;
    }

    public void setDayChange(BigDecimal dayChange) {
        this.dayChange = dayChange;
    }

    public BigDecimal getDayChangePercent() {
        return dayChangePercent;
    }

    public void setDayChangePercent(BigDecimal dayChangePercent) {
        this.dayChangePercent = dayChangePercent;
    }

    public BigDecimal getTop3HoldingPercent() {
        return top3HoldingPercent;
    }

    public void setTop3HoldingPercent(BigDecimal top3HoldingPercent) {
        this.top3HoldingPercent = top3HoldingPercent;
    }

    public BigDecimal getDiversificationScore() {
        return diversificationScore;
    }

    public void setDiversificationScore(BigDecimal diversificationScore) {
        this.diversificationScore = diversificationScore;
    }

}
