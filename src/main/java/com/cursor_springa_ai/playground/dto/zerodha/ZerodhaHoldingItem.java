package com.cursor_springa_ai.playground.dto.zerodha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ZerodhaHoldingItem {

    @JsonProperty("instrument_token")
    private Long instrumentToken;

    @JsonProperty("tradingsymbol")
    private String tradingSymbol;

    private String exchange;

    private String isin;
    /** Kite may return integer or decimal depending on instrument */
    private BigDecimal quantity;

    @JsonProperty("average_price")
    private BigDecimal averagePrice;

    @JsonProperty("last_price")
    private BigDecimal lastPrice;

    @JsonProperty("pnl")
    private BigDecimal pnl;


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


    public Long getInstrumentToken() {
        return instrumentToken;
    }

    public void setInstrumentToken(Long instrumentToken) {
        this.instrumentToken = instrumentToken;
    }

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }
}

