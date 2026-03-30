package com.cursor_springa_ai.playground.integration.market.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NseQuoteResponse(
        @JsonProperty("info")
        Info info,

        @JsonProperty("metadata")
        Metadata metadata,

        @JsonProperty("priceInfo")
        PriceInfo priceInfo,

        @JsonProperty("industryInfo")
        IndustryInfo industryInfo,

        @JsonProperty("securityInfo")
        SecurityInfo securityInfo
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Info(
            @JsonProperty("symbol")
            String symbol,

            @JsonProperty("companyName")
            String companyName,

            @JsonProperty("isETFSec")
            Boolean isETFSec
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(
            @JsonProperty("pdSymbolPe")
            @JsonDeserialize(using = NaToNullDoubleDeserializer.class)
            Double pdSymbolPe,

            @JsonProperty("pdSectorPe")
            @JsonDeserialize(using = NaToNullDoubleDeserializer.class)
            Double pdSectorPe
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriceInfo(
            @JsonProperty("lastPrice")
            @JsonDeserialize(using = NaToNullDoubleDeserializer.class)
            Double lastPrice,

            @JsonProperty("close")
            @JsonDeserialize(using = NaToNullDoubleDeserializer.class)
            Double close,

            @JsonProperty("previousClose")
            @JsonDeserialize(using = NaToNullDoubleDeserializer.class)
            Double previousClose,

            @JsonProperty("pChange")
            @JsonDeserialize(using = NaToNullDoubleDeserializer.class)
            Double pChange,

            @JsonProperty("weekHighLow")
            WeekHighLow weekHighLow
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WeekHighLow(
            @JsonProperty("max")
            @JsonDeserialize(using = NaToNullDoubleDeserializer.class)
            Double max,

            @JsonProperty("min")
            @JsonDeserialize(using = NaToNullDoubleDeserializer.class)
            Double min
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IndustryInfo(
            @JsonProperty("sector")
            String sector,

            @JsonProperty("industry")
            String industry
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SecurityInfo(
            @JsonProperty("issuedSize")
            Long issuedSize
    ) {
    }
}
