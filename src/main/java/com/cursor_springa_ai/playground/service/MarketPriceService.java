package com.cursor_springa_ai.playground.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
/**
 * Fallback price service used when a holding's current price is unavailable.
 * For portfolios imported from Zerodha, the last traded price is always present,
 * so this service is only reached for manually added holdings without a price.
 * Returns a placeholder value of 0 to signal that the price is unknown — callers
 * should surface this to the user rather than silently using a wrong number.
 */
@Service
public class MarketPriceService {

    public BigDecimal getCurrentPrice(String symbol) {
        // No hardcoded prices — live data comes from Zerodha (lastPrice) or NSE API.
        // Return zero as a sentinel so the caller knows the price is unavailable.
        return BigDecimal.ZERO;
    }
}
