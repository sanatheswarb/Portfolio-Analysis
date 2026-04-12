package com.cursor_springa_ai.playground.ai.tools;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Per-instance tool invocation tracker shared by {@link PortfolioReasoningTools} and
 * {@link PortfolioChatReasoningTools}.
 *
 * <p>Tracks how many times each named tool has been called, records which tool was invoked
 * first, and optionally forwards each invocation to a cross-instance
 * {@link ToolInvocationRecorder} so the caller can observe ordering across multiple tool
 * objects registered in the same LLM call.
 */
public final class ToolCallTracker {

    private final Logger logger;
    private final String logPrefix;
    private final ToolInvocationRecorder sharedRecorder;

    private final Map<String, Integer> invocationCounts = new LinkedHashMap<>();
    private int totalInvocations;
    private String firstInvokedTool;

    /**
     * @param ownerClass     class whose name is used for the JUL logger
     * @param logPrefix      prefix printed before the tool name on every invocation
     * @param sharedRecorder optional cross-instance recorder; may be {@code null}
     */
    public ToolCallTracker(Class<?> ownerClass, String logPrefix, ToolInvocationRecorder sharedRecorder) {
        this.logger = Logger.getLogger(
                Objects.requireNonNull(ownerClass, "ownerClass must not be null").getName());
        this.logPrefix = Objects.requireNonNull(logPrefix, "logPrefix must not be null");
        this.sharedRecorder = sharedRecorder;
    }

    /**
     * Records a single tool invocation.
     *
     * @param toolName the name of the tool that was invoked; must not be {@code null}
     */
    public void record(String toolName) {
        String safeToolName = Objects.requireNonNull(toolName, "toolName must not be null");
        totalInvocations++;
        if (firstInvokedTool == null) {
            firstInvokedTool = safeToolName;
        }
        if (sharedRecorder != null) {
            sharedRecorder.record(safeToolName);
        }
        logger.info(logPrefix + safeToolName);
        invocationCounts.merge(safeToolName, 1, Integer::sum);
    }

    /** Total number of tool invocations recorded by this instance. */
    public int invocationCount() {
        return totalInvocations;
    }

    /** Unmodifiable snapshot of per-tool invocation counts. */
    public Map<String, Integer> invocationCounts() {
        return Map.copyOf(invocationCounts);
    }

    /** The first tool name recorded, or {@code null} if no tool has been invoked yet. */
    public String firstInvokedTool() {
        return firstInvokedTool;
    }

    /** Returns {@code true} if the named tool has been invoked at least once. */
    public boolean hasInvokedTool(String toolName) {
        return invocationCounts.containsKey(toolName);
    }
}
