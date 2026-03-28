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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Snapshot of a single holding for a user, written (upserted) on every holdings import.
 * One row per (user, instrument) pair — the latest import always overwrites the previous snapshot.
 */
@Entity
@Table(
        name = "user_holdings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_holdings_user_instrument",
                columnNames = {"user_id", "instrument_token"}
        )
)
public class UserHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_token", nullable = false)
    private Instrument instrument;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "avg_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal avgPrice;

    @Column(name = "last_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal lastPrice;

    @Column(name = "invested_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal investedValue;

    @Column(name = "current_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal currentValue;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal pnl;

    @Column(name = "pnl_percent", precision = 10, scale = 4)
    private BigDecimal pnlPercent;

    @Column(name = "day_change", precision = 18, scale = 4)
    private BigDecimal dayChange;

    @Column(name = "day_change_percent", precision = 10, scale = 4)
    private BigDecimal dayChangePercent;

    @Column(name = "weight_percent", precision = 10, scale = 4)
    private BigDecimal weightPercent;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UserHolding() {
    }

    public UserHolding(User user, Instrument instrument, Integer quantity,
                       BigDecimal avgPrice, BigDecimal lastPrice,
                       BigDecimal investedValue, BigDecimal currentValue,
                       BigDecimal pnl, BigDecimal pnlPercent,
                       BigDecimal dayChange, BigDecimal dayChangePercent) {
        this.user = user;
        this.instrument = instrument;
        this.quantity = quantity;
        this.avgPrice = avgPrice;
        this.lastPrice = lastPrice;
        this.investedValue = investedValue;
        this.currentValue = currentValue;
        this.pnl = pnl;
        this.pnlPercent = pnlPercent;
        this.dayChange = dayChange;
        this.dayChangePercent = dayChangePercent;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    public void setAvgPrice(BigDecimal avgPrice) {
        this.avgPrice = avgPrice;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public BigDecimal getInvestedValue() {
        return investedValue;
    }

    public void setInvestedValue(BigDecimal investedValue) {
        this.investedValue = investedValue;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }

    public BigDecimal getPnl() {
        return pnl;
    }

    public void setPnl(BigDecimal pnl) {
        this.pnl = pnl;
    }

    public BigDecimal getPnlPercent() {
        return pnlPercent;
    }

    public void setPnlPercent(BigDecimal pnlPercent) {
        this.pnlPercent = pnlPercent;
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

    public BigDecimal getWeightPercent() {
        return weightPercent;
    }

    public void setWeightPercent(BigDecimal weightPercent) {
        this.weightPercent = weightPercent;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
