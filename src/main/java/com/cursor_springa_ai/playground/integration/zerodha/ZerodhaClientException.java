package com.cursor_springa_ai.playground.integration.zerodha;

public class ZerodhaClientException extends RuntimeException {

    public ZerodhaClientException(String message) {
        super(message);
    }

    public ZerodhaClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
