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
 * Fundamental financial ratios for a single instrument.
 * Shares the instrument_token PK with {@link Instrument} (one-to-one).
 *
 * <p>pe, market_cap, and sector are populated from the NSE quote API.
 * pb, roe, and debt_to_equity require financial-statement data not available from
 * the NSE quote endpoint; they are left null until an additional enrichment source
 * is wired in.
 */
@Entity
@Table(name = "stock_fundamentals")
public class StockFundamentals {

    @Id
    @Column(name = "instrument_token")
    private Long instrumentToken;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "instrument_token", nullable = false)
    private Instrument instrument;

    /** Price-to-Earnings ratio (NSE: metadata.pdSymbolPe). */
    @Column(precision = 10, scale = 4)
    private BigDecimal pe;

    /** Price-to-Book ratio — requires balance-sheet data; nullable. */
    @Column(precision = 10, scale = 4)
    private BigDecimal pb;

    /** Return on Equity — requires income-statement data; nullable. */
    @Column(precision = 10, scale = 4)
    private BigDecimal roe;

    /** Debt-to-Equity ratio — requires balance-sheet data; nullable. */
    @Column(name = "debt_to_equity", precision = 10, scale = 4)
    private BigDecimal debtToEquity;

    /** Absolute market capitalisation in INR (issuedSize × lastPrice). */
    @Column(name = "market_cap")
    private Long marketCap;

    /** Macro sector from NSE industryInfo.sector (e.g. "INFORMATION TECHNOLOGY"). */
    @Column(length = 100)
    private String sector;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    protected StockFundamentals() {
    }

    public StockFundamentals(Instrument instrument) {
        this.instrument = instrument;
        this.instrumentToken = instrument.getInstrumentToken();
    }

    public Long getInstrumentToken() {
        return instrumentToken;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public BigDecimal getPe() {
        return pe;
    }

    public void setPe(BigDecimal pe) {
        this.pe = pe;
    }

    public BigDecimal getPb() {
        return pb;
    }

    public void setPb(BigDecimal pb) {
        this.pb = pb;
    }

    public BigDecimal getRoe() {
        return roe;
    }

    public void setRoe(BigDecimal roe) {
        this.roe = roe;
    }

    public BigDecimal getDebtToEquity() {
        return debtToEquity;
    }

    public void setDebtToEquity(BigDecimal debtToEquity) {
        this.debtToEquity = debtToEquity;
    }

    public Long getMarketCap() {
        return marketCap;
    }

    public void setMarketCap(Long marketCap) {
        this.marketCap = marketCap;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
