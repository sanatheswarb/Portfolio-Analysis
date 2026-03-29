package com.cursor_springa_ai.playground.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class Portfolio {

    private String id;
    private Map<String, Holding> holdings = new LinkedHashMap<>();

    public Portfolio() {
    }

    public Portfolio(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Holding> getHoldings() {
        return holdings;
    }

    public void setHoldings(Map<String, Holding> holdings) {
        this.holdings = holdings;
    }
}
