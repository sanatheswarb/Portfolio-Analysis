package com.cursor_springa_ai.playground.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRequestTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void tearDown() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void question_mustNotBeBlank() {
        ChatRequest request = new ChatRequest();
        request.setQuestion("   ");

        var violations = VALIDATOR.validate(request);

        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Question cannot be empty"));
    }
}
