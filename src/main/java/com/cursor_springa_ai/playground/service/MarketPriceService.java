package com.cursor_springa_ai.playground.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

@Service
public class MarketPriceService {

    private static final Map<String, BigDecimal> SAMPLE_PRICES = Map.of(
            "AAPL", BigDecimal.valueOf(210.35),
            "MSFT", BigDecimal.valueOf(425.20),
            "GOOGL", BigDecimal.valueOf(182.12),
            "AMZN", BigDecimal.valueOf(198.65),
            "TSLA", BigDecimal.valueOf(225.40),
            "NVDA", BigDecimal.valueOf(905.75),
            "SPY", BigDecimal.valueOf(540.22),
            "QQQ", BigDecimal.valueOf(465.55),
            "BTC", BigDecimal.valueOf(68000.00),
            "ETH", BigDecimal.valueOf(3500.00)
    );

    public BigDecimal getCurrentPrice(String symbol) {
        String upperSymbol = symbol.toUpperCase(Locale.ROOT);
        return SAMPLE_PRICES.getOrDefault(upperSymbol, BigDecimal.valueOf(100.00));
    }
}
