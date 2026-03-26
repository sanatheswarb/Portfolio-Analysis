package com.cursor_springa_ai.playground.integration.zerodha.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ZerodhaHoldingItem {

    @JsonProperty("tradingsymbol")
    private String tradingSymbol;

    private String exchange;
    /** Kite may return integer or decimal depending on instrument */
    private BigDecimal quantity;

    @JsonProperty("average_price")
    private BigDecimal averagePrice;

    @JsonProperty("last_price")
    private BigDecimal lastPrice;

    @JsonProperty("close_price")
    private BigDecimal closePrice;

    @JsonProperty("pnl")
    private BigDecimal pnl;

    @JsonProperty("day_change")
    private BigDecimal dayChange;

    @JsonProperty("day_change_percentage")
    private BigDecimal dayChangePercentage;

    private String profitLoss;

    public String getTradingSymbol() {
        return tradingSymbol;
    }

    public void setTradingSymbol(String tradingSymbol) {
        this.tradingSymbol = tradingSymbol;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAveragePrice() {
        return averagePrice;
    }

    public void setAveragePrice(BigDecimal averagePrice) {
        this.averagePrice = averagePrice;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(BigDecimal closePrice) {
        this.closePrice = closePrice;
    }

    public BigDecimal getPnl() {
        return pnl;
    }

    public void setPnl(BigDecimal pnl) {
        this.pnl = pnl;
    }

    public String getProfitLoss() {
        return profitLoss;
    }

    public void setProfitLoss(String profitLoss) {
        this.profitLoss = profitLoss;
    }

    public BigDecimal getDayChange() {
        return dayChange;
    }

    public void setDayChange(BigDecimal dayChange) {
        this.dayChange = dayChange;
    }

    public BigDecimal getDayChangePercentage() {
        return dayChangePercentage;
    }

    public void setDayChangePercentage(BigDecimal dayChangePercentage) {
        this.dayChangePercentage = dayChangePercentage;
    }
}
