package com.cursor_springa_ai.playground.ai.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RiskFlagPrioritizer {

    private static final Map<String, Integer> RISK_PRIORITY = riskPriority();

    private RiskFlagPrioritizer() {
    }

    public static String primaryRisk(List<String> riskFlags) {
        if (riskFlags == null || riskFlags.isEmpty()) {
            return null;
        }
        return riskFlags.stream()
                .min((left, right) -> Integer.compare(priority(left), priority(right)))
                .orElse(null);
    }

    public static List<String> sortByPriority(List<String> riskFlags) {
        if (riskFlags == null || riskFlags.isEmpty()) {
            return List.of();
        }
        return riskFlags.stream()
                .sorted((left, right) -> Integer.compare(priority(left), priority(right)))
                .toList();
    }

    public static int priority(String riskFlag) {
        return RISK_PRIORITY.getOrDefault(riskFlag, Integer.MAX_VALUE);
    }

    private static Map<String, Integer> riskPriority() {
        Map<String, Integer> priority = new LinkedHashMap<>();
        priority.put("HIGH_CONCENTRATION", 1);
        priority.put("UNDER_DIVERSIFIED", 2);
        priority.put("TOP_HEAVY_PORTFOLIO", 3);
        priority.put("SMALL_CAP_RISK", 4);
        priority.put("HIGH_VALUATION", 5);
        priority.put("DEEP_CORRECTION", 6);
        priority.put("PROFIT_BOOKING_ZONE", 7);
        return Map.copyOf(priority);
    }
}
