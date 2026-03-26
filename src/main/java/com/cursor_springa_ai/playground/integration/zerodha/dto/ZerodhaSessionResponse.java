package com.cursor_springa_ai.playground.integration.zerodha.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ZerodhaSessionResponse {

    private String status;
    private ZerodhaSessionData data;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ZerodhaSessionData getData() {
        return data;
    }

    public void setData(ZerodhaSessionData data) {
        this.data = data;
    }
}
