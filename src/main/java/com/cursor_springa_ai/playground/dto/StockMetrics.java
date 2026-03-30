package com.cursor_springa_ai.playground.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record StockMetrics(
        @JsonProperty("symbol")
        String symbol,

        @JsonProperty("sector")
        String sector,

        @JsonProperty("market_cap_type")
        String marketCapType,

        @JsonProperty("pe")
        BigDecimal pe,

        @JsonProperty("beta")
        BigDecimal beta,

        @JsonProperty("week52_high")
        BigDecimal week52High,

        @JsonProperty("week52_low")
        BigDecimal week52Low,

        @JsonProperty("sector_pe")
        BigDecimal sectorPe,

        @JsonProperty("issued_size")
        Long issuedSize,

        @JsonProperty("dma200")
        BigDecimal dma200,

        @JsonProperty("last_price")
        BigDecimal lastPrice
) {
}
