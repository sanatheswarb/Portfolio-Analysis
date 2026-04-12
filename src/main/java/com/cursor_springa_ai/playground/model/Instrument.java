package com.cursor_springa_ai.playground.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Persisted catalogue of financial instruments seen during holdings imports.
 * Uses an internal surrogate primary key while retaining broker/exchange identifiers
 * such as instrument_token and ISIN as lookup fields.
 */
@Entity
@Table(name = "instruments")
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "instrument_id", nullable = false)
    private Long id;

    /** Zerodha instrument token used as an external lookup identifier. */
    @Column(name = "instrument_token", unique = true)
    private Long instrumentToken;

    @Column(nullable = false, length = 50)
    private String symbol;

    @Column(nullable = false, length = 20)
    private String exchange;

    @Column(length = 20, unique = true)
    private String isin;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(length = 100)
    private String sector;

    @Column(length = 100)
    private String industry;

    @Column(name = "market_cap_category", length = 20)
    private String marketCapCategory;

    @CreationTimestamp
    @Column(name = "first_seen", nullable = false, updatable = false)
    private LocalDateTime firstSeen;

    @Column(name = "last_enriched")
    private LocalDateTime lastEnriched;

    @OneToOne(mappedBy = "instrument", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private StockFundamentals stockFundamentals;

    protected Instrument() {
    }

    public Instrument(Long instrumentToken, String symbol, String exchange, String isin) {
        this.instrumentToken = instrumentToken;
        this.symbol = symbol;
        this.exchange = exchange;
        this.isin = isin;
    }

    public Long getId() {
        return id;
    }

    public Long getInstrumentToken() {
        return instrumentToken;
    }

    public void setInstrumentToken(Long instrumentToken) {
        this.instrumentToken = instrumentToken;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getMarketCapCategory() {
        return marketCapCategory;
    }

    public void setMarketCapCategory(String marketCapCategory) {
        this.marketCapCategory = marketCapCategory;
    }

    public LocalDateTime getFirstSeen() {
        return firstSeen;
    }

    public LocalDateTime getLastEnriched() {
        return lastEnriched;
    }

    public void setLastEnriched(LocalDateTime lastEnriched) {
        this.lastEnriched = lastEnriched;
    }

    public StockFundamentals getStockFundamentals() {
        return stockFundamentals;
    }

    public void setStockFundamentals(StockFundamentals stockFundamentals) {
        this.stockFundamentals = stockFundamentals;
    }
}
