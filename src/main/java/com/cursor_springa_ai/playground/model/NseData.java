package com.cursor_springa_ai.playground.model;

import com.cursor_springa_ai.playground.dto.StockMetrics;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "nse_data")
public class NseData {

    @Id
    @Column(nullable = false)
    private String symbol;

    private String sector;

    @Column(name = "market_cap_type")
    private String marketCapType;

    private BigDecimal pe;

    private BigDecimal beta;

    @Column(name = "week52_high")
    private BigDecimal week52High;

    @Column(name = "week52_low")
    private BigDecimal week52Low;

    @Column(name = "sector_pe")
    private BigDecimal sectorPe;

    @Column(name = "issued_size")
    private Long issuedSize;

    private BigDecimal dma200;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public NseData() {
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static NseData fromStockMetrics(String symbol, StockMetrics metrics) {
        NseData data = new NseData();
        data.symbol = symbol.toUpperCase();
        data.sector = metrics.sector();
        data.marketCapType = metrics.marketCapType();
        data.pe = metrics.pe();
        data.beta = metrics.beta();
        data.week52High = metrics.week52High();
        data.week52Low = metrics.week52Low();
        data.sectorPe = metrics.sectorPe();
        data.issuedSize = metrics.issuedSize();
        data.dma200 = metrics.dma200();
        return data;
    }

    public StockMetrics toStockMetrics() {
        return new StockMetrics(symbol, sector, marketCapType, pe, beta, week52High, week52Low, sectorPe, issuedSize, dma200);
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public String getMarketCapType() {
        return marketCapType;
    }

    public void setMarketCapType(String marketCapType) {
        this.marketCapType = marketCapType;
    }

    public BigDecimal getPe() {
        return pe;
    }

    public void setPe(BigDecimal pe) {
        this.pe = pe;
    }

    public BigDecimal getBeta() {
        return beta;
    }

    public void setBeta(BigDecimal beta) {
        this.beta = beta;
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

    public Long getIssuedSize() {
        return issuedSize;
    }

    public void setIssuedSize(Long issuedSize) {
        this.issuedSize = issuedSize;
    }

    public BigDecimal getDma200() {
        return dma200;
    }

    public void setDma200(BigDecimal dma200) {
        this.dma200 = dma200;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
