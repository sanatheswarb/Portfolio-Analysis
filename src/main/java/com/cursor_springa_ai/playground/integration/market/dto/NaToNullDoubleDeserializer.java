package com.cursor_springa_ai.playground.integration.market.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * Custom deserializer that converts "NA" strings to null for Double values.
 * Handles cases where NSE API returns "NA" instead of numeric values.
 */
public class NaToNullDoubleDeserializer extends JsonDeserializer<Double> {

    @Override
    public Double deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();
        
        // Convert "NA" or empty strings to null
        if (value == null || value.trim().isEmpty() || "NA".equalsIgnoreCase(value)) {
            return null;
        }
        
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
