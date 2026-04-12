package com.cursor_springa_ai.playground.exception;

/**
 * Thrown when an operation requires an authenticated Zerodha session but none is active.
 * Mapped to HTTP 401 Unauthorized by {@link com.cursor_springa_ai.playground.controller.ApiExceptionHandler}.
 */
public class NotAuthenticatedException extends RuntimeException {

    public NotAuthenticatedException(String message) {
        super(message);
    }
}

