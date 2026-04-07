package com.cursor_springa_ai.playground.ai.reasoning;

import java.util.Objects;

public class ToolInvocationRecorder {

    private String firstInvokedTool;

    public void record(String toolName) {
        String safeToolName = Objects.requireNonNull(toolName, "toolName must not be null");
        if (firstInvokedTool == null) {
            firstInvokedTool = safeToolName;
        }
    }

    public String firstInvokedTool() {
        return firstInvokedTool;
    }
}
