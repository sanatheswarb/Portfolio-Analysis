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
import java.time.LocalDateTime;

/**
 * Derived market metrics for one holding in a user's portfolio.
 * Recalculated after every holdings import — one row per (user, instrument).
 */
@Entity
@Table(
        name = "user_stock_metrics",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_stock_metrics_user_instrument",
                columnNames = {"user_id", "instrument_token"}
        )
)
public class UserStockMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_token", nullable = false)
    private Instrument instrument;

    /** OVERVALUED / UNDERVALUED / FAIRLY_VALUED — derived from stock PE vs sector PE. */
    @Column(name = "valuation_flag", length = 20)
    private String valuationFlag;

    /** Normalised risk score 1–10 (higher = riskier); based on daily-return volatility. */
    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    /** Momentum score 0–100: (lastPrice / week52High) × 100. */
    @Column(name = "momentum_score", precision = 6, scale = 2)
    private BigDecimal momentumScore;

    /** Short-term volatility proxy: |dayChangePercent| from the last import. */
    @Column(precision = 10, scale = 4)
    private BigDecimal volatility;

    /** This holding's weight in the user's portfolio as a percentage. */
    @Column(name = "weight_percent", precision = 10, scale = 4)
    private BigDecimal weightPercent;

    /** 52-week high price sourced from the NSE market metrics cache. */
    @Column(name = "week52_high", precision = 18, scale = 4)
    private BigDecimal week52High;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    protected UserStockMetrics() {
    }

    public UserStockMetrics(User user, Instrument instrument) {
        this.user = user;
        this.instrument = instrument;
    }

    public Long getId() { return id; }

    public User getUser() { return user; }

    public Instrument getInstrument() { return instrument; }

    public String getValuationFlag() { return valuationFlag; }

    public void setValuationFlag(String valuationFlag) { this.valuationFlag = valuationFlag; }

    public BigDecimal getRiskScore() { return riskScore; }

    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }

    public BigDecimal getMomentumScore() { return momentumScore; }

    public void setMomentumScore(BigDecimal momentumScore) { this.momentumScore = momentumScore; }

    public BigDecimal getVolatility() { return volatility; }

    public void setVolatility(BigDecimal volatility) { this.volatility = volatility; }

    public BigDecimal getWeightPercent() { return weightPercent; }

    public void setWeightPercent(BigDecimal weightPercent) { this.weightPercent = weightPercent; }

    public BigDecimal getWeek52High() { return week52High; }

    public void setWeek52High(BigDecimal week52High) { this.week52High = week52High; }

    public LocalDateTime getCalculatedAt() { return calculatedAt; }

    public void setCalculatedAt(LocalDateTime calculatedAt) { this.calculatedAt = calculatedAt; }
}
