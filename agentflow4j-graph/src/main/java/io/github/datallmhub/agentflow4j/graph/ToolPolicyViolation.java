package io.github.datallmhub.agentflow4j.graph;

import java.util.Map;
import java.util.Objects;

/**
 * Thrown by a wrapper {@code ToolCallback} when a {@link ToolPolicy} denies a
 * tool call. The exception carries the tool name, the parsed arguments and the
 * denial reason so audit pipelines can record full context.
 */
public final class ToolPolicyViolation extends RuntimeException {

    private final String toolName;
    private final Map<String, Object> arguments;
    private final String reason;

    public ToolPolicyViolation(String toolName, Map<String, Object> arguments, String reason) {
        super("tool policy denied call to '" + toolName + "': " + reason);
        this.toolName = Objects.requireNonNull(toolName, "toolName");
        this.arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public String toolName() {
        return toolName;
    }

    public Map<String, Object> arguments() {
        return arguments;
    }

    public String reason() {
        return reason;
    }
}
