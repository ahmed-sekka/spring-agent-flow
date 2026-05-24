package io.github.datallmhub.agentflow4j.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link RunLogStore} keyed by run id. Suitable for development,
 * tests, and single-process deployments. Events for a run accumulate until
 * {@link #clear(String)} or {@link #clearAll()} is called — bound the
 * retention yourself if runs are long-lived or high-volume.
 */
public final class InMemoryRunLogStore implements RunLogStore {

    private final Map<String, List<AgentRunEvent>> byRun = new ConcurrentHashMap<>();

    @Override
    public void append(AgentRunEvent event) {
        byRun.computeIfAbsent(event.runId(), k -> new CopyOnWriteArrayList<>()).add(event);
    }

    @Override
    public List<AgentRunEvent> events(String runId) {
        List<AgentRunEvent> events = byRun.get(runId);
        return events == null ? List.of() : List.copyOf(events);
    }

    @Override
    public List<String> runIds() {
        return new ArrayList<>(byRun.keySet());
    }

    /** Drops the events recorded for one run. */
    public void clear(String runId) {
        byRun.remove(runId);
    }

    /** Drops everything. */
    public void clearAll() {
        byRun.clear();
    }
}
