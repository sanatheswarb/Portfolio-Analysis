package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.dto.ai.NewsImpact;
import com.cursor_springa_ai.playground.dto.ai.NewsMateriality;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewsClassifierTest {

    private final NewsClassifier classifier = new NewsClassifier();

    @Test
    void classifyImpact_identifiesNegativePositiveAndNeutral() {
        assertEquals(NewsImpact.NEGATIVE, classifier.classifyImpact("Company faces fraud investigation"));
        assertEquals(NewsImpact.POSITIVE, classifier.classifyImpact("Company reports profit growth"));
        assertEquals(NewsImpact.NEUTRAL, classifier.classifyImpact("Company attends conference"));
    }

    @Test
    void classifyMateriality_identifiesHighMediumAndLow() {
        assertEquals(NewsMateriality.HIGH, classifier.classifyMateriality("Regulatory investigation launched"));
        assertEquals(NewsMateriality.MEDIUM, classifier.classifyMateriality("Earnings guidance improves"));
        assertEquals(NewsMateriality.LOW, classifier.classifyMateriality("Leadership interview published"));
    }

    @Test
    void isRiskRelevant_requiresNegativeAndNonLowMateriality() {
        assertTrue(classifier.isRiskRelevant(NewsImpact.NEGATIVE, NewsMateriality.HIGH));
        assertTrue(classifier.isRiskRelevant(NewsImpact.NEGATIVE, NewsMateriality.MEDIUM));
        assertFalse(classifier.isRiskRelevant(NewsImpact.NEGATIVE, NewsMateriality.LOW));
        assertFalse(classifier.isRiskRelevant(NewsImpact.POSITIVE, NewsMateriality.HIGH));
    }
}
