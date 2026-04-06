package com.cursor_springa_ai.playground.ai.reasoning;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ToolInvocationRecorder {

    private final List<String> toolInvocationOrder = new ArrayList<>();

    public void record(String toolName) {
        toolInvocationOrder.add(Objects.requireNonNull(toolName, "toolName must not be null"));
    }

    public String firstInvokedTool() {
        return toolInvocationOrder.isEmpty() ? null : toolInvocationOrder.getFirst();
    }
}
