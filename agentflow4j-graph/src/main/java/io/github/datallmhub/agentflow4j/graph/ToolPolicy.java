package io.github.datallmhub.agentflow4j.graph;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;

import org.jspecify.annotations.Nullable;

/**
 * Allow / deny gate evaluated <em>before</em> a tool is executed. Implementations
 * are pure functions of {@code (toolName, arguments)} so they can be unit-tested
 * without booting Spring AI.
 *
 * <p>A denied call surfaces as a {@code ToolPolicyViolation} thrown from the
 * wrapping {@code ToolCallback}, which Spring AI records as a tool failure that
 * the model can react to (retry, skip, escalate).
 *
 * <p>This SPI is intentionally narrow. It does not modify arguments, sandbox
 * execution, or change the result — use a downstream policy (state, budget,
 * approval) for those concerns.
 */
@FunctionalInterface
public interface ToolPolicy {

    Decision check(String toolName, Map<String, Object> arguments);

    /** Every call allowed. Useful as a default and in tests. */
    ToolPolicy ALLOW_ALL = (name, args) -> Decision.allow();

    /** Every call denied. Useful for locking an agent down by mistake. */
    ToolPolicy DENY_ALL = (name, args) -> Decision.deny("DENY_ALL policy");

    /**
     * Allow only the listed tool names; deny everything else.
     */
    static ToolPolicy allowList(String... allowed) {
        Set<String> set = new HashSet<>(Arrays.asList(Objects.requireNonNull(allowed, "allowed")));
        return (name, args) -> set.contains(name)
                ? Decision.allow()
                : Decision.deny("tool '" + name + "' is not in the allow-list");
    }

    /**
     * Deny the listed tool names; allow everything else.
     */
    static ToolPolicy denyList(String... denied) {
        Set<String> set = new HashSet<>(Arrays.asList(Objects.requireNonNull(denied, "denied")));
        return (name, args) -> set.contains(name)
                ? Decision.deny("tool '" + name + "' is denied")
                : Decision.allow();
    }

    /**
     * Build a custom rule using a {@link BiPredicate} on
     * {@code (toolName, arguments)}.
     */
    static ToolPolicy when(BiPredicate<String, Map<String, Object>> predicate, String denialReason) {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(denialReason, "denialReason");
        return (name, args) -> predicate.test(name, args)
                ? Decision.allow()
                : Decision.deny(denialReason);
    }

    /**
     * Compose two policies — both must allow for the call to pass. Useful for
     * stacking, e.g. {@code allowList(...).and(custom(...))}.
     */
    default ToolPolicy and(ToolPolicy other) {
        Objects.requireNonNull(other, "other");
        return (name, args) -> {
            Decision first = this.check(name, args);
            return first.denied() ? first : other.check(name, args);
        };
    }

    /** Outcome of a {@link #check} call. */
    record Decision(boolean allowed, @Nullable String reason) {

        public static Decision allow() {
            return new Decision(true, null);
        }

        public static Decision deny(String reason) {
            return new Decision(false, Objects.requireNonNull(reason, "reason"));
        }

        public boolean denied() {
            return !allowed;
        }
    }
}
