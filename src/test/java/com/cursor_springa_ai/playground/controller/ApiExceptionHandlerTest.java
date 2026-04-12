package com.cursor_springa_ai.playground.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleResponseStatus_preservesStatusAndReason() {
        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.CONFLICT, "Run portfolio analysis first"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Run portfolio analysis first", response.getBody().get("message"));
        assertEquals(HttpStatus.CONFLICT.value(), response.getBody().get("status"));
    }
}
