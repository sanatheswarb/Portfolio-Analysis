package com.cursor_springa_ai.playground.util;

import java.util.Locale;

/**
 * Shared string normalization utility.
 *
 * <p>Trims surrounding whitespace and uppercases using {@link Locale#ROOT}.
 * Returns {@code null} for null or blank input so callers can treat missing values uniformly.
 */
public final class StringNormalizer {

    private StringNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}

