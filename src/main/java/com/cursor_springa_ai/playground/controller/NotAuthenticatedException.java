package com.cursor_springa_ai.playground.controller;

/**
 * Thrown when an operation requires an authenticated Zerodha session but none is active.
 * Mapped to HTTP 401 Unauthorized by {@link ApiExceptionHandler}.
 */
public class NotAuthenticatedException extends RuntimeException {

    public NotAuthenticatedException(String message) {
        super(message);
    }
}
