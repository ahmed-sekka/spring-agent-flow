package io.github.datallmhub.agentflow4j.graph;

import java.util.Set;

import io.github.datallmhub.agentflow4j.core.AgentContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalGateTests {

    @Test
    void noneAlwaysAllows() {
        assertThat(ApprovalGate.NONE.check("anything", AgentContext.empty()).requiresApproval())
                .isFalse();
    }

    @Test
    void requireForFlagsListedNodes() {
        ApprovalGate gate = ApprovalGate.requireFor("payment.transfer", "refund.process");

        ApprovalGate.Decision blocked = gate.check("payment.transfer", AgentContext.empty());
        assertThat(blocked.requiresApproval()).isTrue();
        assertThat(blocked.reason()).contains("payment.transfer").contains("approval");

        assertThat(gate.check("logging.write", AgentContext.empty()).requiresApproval()).isFalse();
    }

    @Test
    void requireForBypassesWhenApprovalMarkerIsPresent() {
        ApprovalGate gate = ApprovalGate.requireFor("payment.transfer");

        AgentContext approved = AgentContext.empty()
                .with(ApprovalGate.APPROVED_KEY, Set.of("payment.transfer"));

        assertThat(gate.check("payment.transfer", approved).requiresApproval()).isFalse();
    }

    @Test
    void whenPredicateTriggersAndBypassMarkerStillApplies() {
        ApprovalGate gate = ApprovalGate.when(
                (node, ctx) -> node.startsWith("payment."),
                "payments need human OK");

        assertThat(gate.check("payment.transfer", AgentContext.empty()).requiresApproval()).isTrue();
        assertThat(gate.check("logging.write", AgentContext.empty()).requiresApproval()).isFalse();

        AgentContext approved = AgentContext.empty()
                .with(ApprovalGate.APPROVED_KEY, Set.of("payment.transfer"));
        assertThat(gate.check("payment.transfer", approved).requiresApproval()).isFalse();
    }

    @Test
    void andComposesGatesAndKeepsFirstReason() {
        ApprovalGate first = ApprovalGate.requireFor("a");
        ApprovalGate second = ApprovalGate.requireFor("b");
        ApprovalGate combined = first.and(second);

        ApprovalGate.Decision dA = combined.check("a", AgentContext.empty());
        assertThat(dA.requiresApproval()).isTrue();
        assertThat(dA.reason()).contains("'a'");

        ApprovalGate.Decision dB = combined.check("b", AgentContext.empty());
        assertThat(dB.requiresApproval()).isTrue();
        assertThat(dB.reason()).contains("'b'");

        assertThat(combined.check("c", AgentContext.empty()).requiresApproval()).isFalse();
    }

    @Test
    void isApprovedHelperReadsMarker() {
        assertThat(ApprovalGate.isApproved(AgentContext.empty(), "x")).isFalse();
        AgentContext withMarker = AgentContext.empty()
                .with(ApprovalGate.APPROVED_KEY, Set.of("x"));
        assertThat(ApprovalGate.isApproved(withMarker, "x")).isTrue();
        assertThat(ApprovalGate.isApproved(withMarker, "y")).isFalse();
    }

    @Test
    void decisionFactoryRequiresNonNullReason() {
        try {
            ApprovalGate.Decision.require(null);
            assertThat(false).as("expected NullPointerException").isTrue();
        }
        catch (NullPointerException expected) {
            // OK
        }
    }
}
