package com.cursor_springa_ai.playground.importer;

import java.util.Locale;

/**
 * Shared normalization for Zerodha trading symbols used during import.
 *
 * <p>Normalization trims surrounding whitespace and uppercases using {@link Locale#ROOT}
 * so symbol filtering, logging, calculations, and persistence all operate on the same value.
 */
final class TradingSymbolNormalizer {

    private TradingSymbolNormalizer() {
    }

    static String normalize(String symbol) {
        return symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
