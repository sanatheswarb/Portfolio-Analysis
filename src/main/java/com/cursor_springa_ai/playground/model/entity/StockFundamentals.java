package com.cursor_springa_ai.playground.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Fundamental financial ratios for a single instrument.
 * Uses its own surrogate primary key and a unique instrument_id foreign key.
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "instrument_id", nullable = false, unique = true)
    private Long instrumentId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false, insertable = false, updatable = false)
    private Instrument instrument;

    /** Trading symbol for readability (denormalized from instrument). */
    @Column(length = 50)
    private String symbol;

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

    /** 52-week high price (NSE: priceInfo.weekHighLow.max). */
    @Column(name = "week52_high", precision = 14, scale = 4)
    private BigDecimal week52High;

    /** 52-week low price (NSE: priceInfo.weekHighLow.min). */
    @Column(name = "week52_low", precision = 14, scale = 4)
    private BigDecimal week52Low;

    /** Sector P/E ratio (NSE: metadata.pdSectorPe). */
    @Column(name = "sector_pe", precision = 10, scale = 4)
    private BigDecimal sectorPe;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    protected StockFundamentals() {
    }

    public StockFundamentals(Instrument instrument) {
        this.instrument = instrument;
        this.instrumentId = instrument != null ? instrument.getId() : null;
    }

    public Long getId() {
        return id;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public void setInstrument(Instrument instrument) {
        this.instrument = instrument;
        this.instrumentId = instrument != null ? instrument.getId() : null;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
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

    public BigDecimal getWeek52High() {
        return week52High;
    }

    public void setWeek52High(BigDecimal week52High) {
        this.week52High = week52High;
    }

    public BigDecimal getWeek52Low() {
        return week52Low;
    }

    public void setWeek52Low(BigDecimal week52Low) {
        this.week52Low = week52Low;
    }

    public BigDecimal getSectorPe() {
        return sectorPe;
    }

    public void setSectorPe(BigDecimal sectorPe) {
        this.sectorPe = sectorPe;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
