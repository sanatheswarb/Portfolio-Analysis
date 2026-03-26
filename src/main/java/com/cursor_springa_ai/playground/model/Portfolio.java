package com.cursor_springa_ai.playground.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class Portfolio {

    private String id;
    private String ownerName;
    private Map<String, Holding> holdings = new LinkedHashMap<>();

    public Portfolio() {
    }

    public Portfolio(String id, String ownerName) {
        this.id = id;
        this.ownerName = ownerName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public Map<String, Holding> getHoldings() {
        return holdings;
    }

    public void setHoldings(Map<String, Holding> holdings) {
        this.holdings = holdings;
    }
}
