package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.ai.NewsImpact;
import com.cursor_springa_ai.playground.dto.ai.NewsMateriality;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class NewsClassifier {

    public NewsImpact classifyImpact(String headline) {
        String h = normalize(headline);

        if (containsAny(h, "loss", "penalty", "fraud", "probe", "investigation", "downgrade", "default")) {
            return NewsImpact.NEGATIVE;
        }

        if (containsAny(h, "profit", "growth", "upgrade", "deal", "order", "expansion")) {
            return NewsImpact.POSITIVE;
        }

        return NewsImpact.NEUTRAL;
    }

    public NewsMateriality classifyMateriality(String headline) {
        String h = normalize(headline);

        if (containsAny(h, "fraud", "investigation", "default", "lawsuit", "regulatory")) {
            return NewsMateriality.HIGH;
        }

        if (containsAny(h, "earnings", "results", "margin", "guidance", "order", "contract")) {
            return NewsMateriality.MEDIUM;
        }

        return NewsMateriality.LOW;
    }

    public boolean isRiskRelevant(NewsImpact impact, NewsMateriality materiality) {
        return impact == NewsImpact.NEGATIVE && materiality != NewsMateriality.LOW;
    }

    private String normalize(String headline) {
        return headline == null ? "" : headline.toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
