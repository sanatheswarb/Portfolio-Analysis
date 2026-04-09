package com.cursor_springa_ai.playground.ai.advisor;

import com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningContext;
import com.cursor_springa_ai.playground.model.PortfolioClassification;
import com.cursor_springa_ai.playground.model.enums.ConcentrationLevel;
import com.cursor_springa_ai.playground.model.enums.DiversificationLevel;
import com.cursor_springa_ai.playground.model.enums.PortfolioRiskLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SuggestionPriorityBuilder {

    private static final List<String> FALLBACK_PRIORITIES = List.of("PORTFOLIO_RESILIENCE", "LONG_TERM_BALANCE");

    public List<String> build(PortfolioReasoningContext context) {
        PortfolioClassification c = context.classification();
        List<String> priorities = new ArrayList<>();

        if (c != null && ConcentrationLevel.CONCENTRATED.equals(c.concentrationLevel())) {
            priorities.add("CONCENTRATION_REDUCTION");
        }

        if (c != null && DiversificationLevel.POOR.equals(c.diversificationLevel())) {
            priorities.add("DIVERSIFICATION_IMPROVEMENT");
        }

        if (c != null && PortfolioRiskLevel.HIGH.equals(c.riskLevel())) {
            priorities.add("RISK_REDUCTION");
        }

        if (priorities.isEmpty()) {
            priorities.add("PORTFOLIO_BALANCING");
        }

        int fallbackIndex = 0;
        while (priorities.size() < 3 && fallbackIndex < FALLBACK_PRIORITIES.size()) {
            String fallback = FALLBACK_PRIORITIES.get(fallbackIndex++);
            if (!priorities.contains(fallback)) {
                priorities.add(fallback);
            }
        }

        return priorities.stream().limit(3).toList();
    }
}
