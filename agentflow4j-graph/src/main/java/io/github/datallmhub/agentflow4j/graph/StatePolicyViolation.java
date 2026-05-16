package io.github.datallmhub.agentflow4j.graph;

import java.util.Objects;

import io.github.datallmhub.agentflow4j.core.StateKey;
import org.jspecify.annotations.Nullable;

/**
 * Thrown by the graph runtime when a {@link StatePolicy} denies a state
 * mutation. Carries the offending key, the proposed value and the denial
 * reason so audit pipelines can capture full context.
 */
public final class StatePolicyViolation extends RuntimeException {

    private final StateKey<?> key;
    @Nullable private final Object value;
    private final String reason;

    public StatePolicyViolation(StateKey<?> key, @Nullable Object value, String reason) {
        super("state policy denied write to '" + key.name() + "': " + reason);
        this.key = Objects.requireNonNull(key, "key");
        this.value = value;
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public StateKey<?> key() {
        return key;
    }

    @Nullable
    public Object value() {
        return value;
    }

    public String reason() {
        return reason;
    }
}
