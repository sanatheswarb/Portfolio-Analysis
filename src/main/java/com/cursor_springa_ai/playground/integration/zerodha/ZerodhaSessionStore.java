package com.cursor_springa_ai.playground.integration.zerodha;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ZerodhaSessionStore {

    private final AtomicReference<String> authorizationHeader = new AtomicReference<>();

    public void saveAuthorizationHeader(String header) {
        authorizationHeader.set(header);
    }

    public Optional<String> getAuthorizationHeader() {
        return Optional.ofNullable(authorizationHeader.get())
                .filter(StringUtils::hasText);
    }
}
