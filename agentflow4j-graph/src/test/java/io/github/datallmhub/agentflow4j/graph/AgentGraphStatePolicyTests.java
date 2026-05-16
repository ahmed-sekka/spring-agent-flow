package io.github.datallmhub.agentflow4j.graph;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.datallmhub.agentflow4j.core.Agent;
import io.github.datallmhub.agentflow4j.core.AgentContext;
import io.github.datallmhub.agentflow4j.core.AgentResult;
import io.github.datallmhub.agentflow4j.core.StateKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentGraphStatePolicyTests {

    private static final StateKey<String>  DRAFT     = StateKey.of("ticket.draft",      String.class);
    private static final StateKey<Boolean> CONFIRMED = StateKey.of("payment.confirmed", Boolean.class);

    @Test
    void allowedWriteFlowsThroughGraphNormally() {
        Agent writer = ctx -> AgentResult.builder()
                .text("done")
                .stateUpdates(Map.of(DRAFT, "hello"))
                .completed(true)
                .build();

        AgentGraph graph = AgentGraph.builder()
                .addNode("writer", writer)
                .statePolicy(StatePolicy.allowWriteKeys("ticket.draft"))
                .build();

        AgentResult result = graph.invoke(AgentContext.of("go"));

        assertThat(result.completed()).isTrue();
        assertThat(result.hasError()).isFalse();
        assertThat(result.text()).isEqualTo("done");
    }

    @Test
    void deniedWriteSurfacesAsAgentErrorUnderFailFast() {
        AtomicInteger calls = new AtomicInteger();
        Agent sneaky = ctx -> {
            calls.incrementAndGet();
            return AgentResult.builder()
                    .text("set confirmed")
                    .stateUpdates(Map.of(CONFIRMED, true))
                    .completed(true)
                    .build();
        };

        AgentGraph graph = AgentGraph.builder()
                .addNode("sneaky", sneaky)
                .statePolicy(StatePolicy.denyWriteKeys("payment.confirmed"))
                .build();

        AgentResult result = graph.invoke(AgentContext.of("go"));

        assertThat(result.hasError()).isTrue();
        assertThat(result.error().cause()).isInstanceOf(StatePolicyViolation.class);
        StatePolicyViolation violation = (StatePolicyViolation) result.error().cause();
        assertThat(violation.key().name()).isEqualTo("payment.confirmed");
        assertThat(violation.value()).isEqualTo(true);
        assertThat(violation.reason()).contains("denied");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void skipNodePolicyContinuesAfterStateDenial() {
        Agent sneaky = ctx -> AgentResult.builder()
                .text("set confirmed")
                .stateUpdates(Map.of(CONFIRMED, true))
                .completed(true)
                .build();
        Agent rescue = ctx -> AgentResult.ofText("recovered");

        AgentGraph graph = AgentGraph.builder()
                .addNode("sneaky", sneaky)
                .addNode("rescue", rescue)
                .addEdge("sneaky", "rescue")
                .errorPolicy(ErrorPolicy.SKIP_NODE)
                .statePolicy(StatePolicy.denyWriteKeys("payment.confirmed"))
                .build();

        AgentResult result = graph.invoke(AgentContext.of("go"));

        assertThat(result.text()).isEqualTo("recovered");
    }

    @Test
    void firstDeniedKeyShortCircuitsTheRest() {
        Agent multi = ctx -> AgentResult.builder()
                .text("multi")
                .stateUpdates(Map.of(
                        DRAFT, "ok",
                        CONFIRMED, true))
                .completed(true)
                .build();

        AgentGraph graph = AgentGraph.builder()
                .addNode("multi", multi)
                .statePolicy(StatePolicy.denyWriteKeys("payment.confirmed"))
                .build();

        AgentResult result = graph.invoke(AgentContext.of("go"));

        assertThat(result.hasError()).isTrue();
        StatePolicyViolation violation = (StatePolicyViolation) result.error().cause();
        assertThat(violation.key().name()).isEqualTo("payment.confirmed");
    }
}
