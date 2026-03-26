package com.cursor_springa_ai.playground.integration.zerodha.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ZerodhaHoldingsResponse {

    private String status;
    private String message;
    @JsonProperty("error_type")
    private String errorType;
    private List<ZerodhaHoldingItem> data;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public List<ZerodhaHoldingItem> getData() {
        return data;
    }

    public void setData(List<ZerodhaHoldingItem> data) {
        this.data = data;
    }
}
