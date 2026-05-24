package io.github.datallmhub.agentflow4j.graph;

import java.util.List;

/**
 * Sink for {@link AgentRunEvent}s emitted during graph execution. The default
 * {@link InMemoryRunLogStore} keeps events in memory keyed by run id; other
 * implementations can stream to a file, a database, or an external pipeline.
 *
 * <p>Implementations must be thread-safe: a single graph instance may run
 * concurrently on multiple threads, each with its own run id.
 */
public interface RunLogStore {

    /** Appends one event. Called in sequence order within a run. */
    void append(AgentRunEvent event);

    /** Returns the events recorded for {@code runId}, in order. Empty if unknown. */
    List<AgentRunEvent> events(String runId);

    /** Returns the run ids this store currently holds events for. */
    List<String> runIds();
}
