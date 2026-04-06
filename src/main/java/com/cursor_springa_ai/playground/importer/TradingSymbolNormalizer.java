package com.cursor_springa_ai.playground.importer;

import java.util.Locale;

final class TradingSymbolNormalizer {

    private TradingSymbolNormalizer() {
    }

    static String normalize(String symbol) {
        return symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
