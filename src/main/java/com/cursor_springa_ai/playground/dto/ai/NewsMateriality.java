package com.cursor_springa_ai.playground.dto.ai;

public enum NewsMateriality {
    LOW(0),
    MEDIUM(1),
    HIGH(2);

    private final int priority;

    NewsMateriality(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
