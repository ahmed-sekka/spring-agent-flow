package io.github.datallmhub.agentflow4j.graph;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolPolicyTests {

    @Test
    void allowListPermitsKnownToolDeniesUnknown() {
        ToolPolicy policy = ToolPolicy.allowList("web.search", "retrieval.search");

        assertThat(policy.check("web.search", Map.of()).allowed()).isTrue();
        assertThat(policy.check("retrieval.search", Map.of()).allowed()).isTrue();

        ToolPolicy.Decision denied = policy.check("shell.execute", Map.of());
        assertThat(denied.denied()).isTrue();
        assertThat(denied.reason()).contains("shell.execute").contains("allow-list");
    }

    @Test
    void denyListDeniesKnownToolPermitsOthers() {
        ToolPolicy policy = ToolPolicy.denyList("shell.execute", "filesystem.write");

        assertThat(policy.check("shell.execute", Map.of()).denied()).isTrue();
        assertThat(policy.check("filesystem.write", Map.of()).denied()).isTrue();
        assertThat(policy.check("web.search", Map.of()).allowed()).isTrue();
    }

    @Test
    void predicateRuleCanInspectArguments() {
        ToolPolicy noLargeWithdrawals = ToolPolicy.when(
                (name, args) -> !("payment.transfer".equals(name)
                        && args.get("amount") instanceof Number n
                        && n.doubleValue() > 1000.0),
                "amount above 1000 requires approval");

        assertThat(noLargeWithdrawals.check("payment.transfer", Map.of("amount", 500.0)).allowed())
                .isTrue();
        ToolPolicy.Decision denied = noLargeWithdrawals.check("payment.transfer",
                Map.of("amount", 5000.0));
        assertThat(denied.denied()).isTrue();
        assertThat(denied.reason()).isEqualTo("amount above 1000 requires approval");
    }

    @Test
    void andStacksTwoPoliciesAndShortCircuitsOnFirstDenial() {
        ToolPolicy first = ToolPolicy.allowList("web.search", "shell.execute");
        ToolPolicy second = ToolPolicy.denyList("shell.execute");
        ToolPolicy combined = first.and(second);

        assertThat(combined.check("web.search", Map.of()).allowed()).isTrue();
        // first allows shell.execute, second denies it -> combined denies
        assertThat(combined.check("shell.execute", Map.of()).denied()).isTrue();
        // first denies retrieval.search -> combined denies without consulting second
        assertThat(combined.check("retrieval.search", Map.of()).denied()).isTrue();
    }

    @Test
    void allowAllAndDenyAllConstantsBehaveAsExpected() {
        assertThat(ToolPolicy.ALLOW_ALL.check("anything", Map.of()).allowed()).isTrue();
        assertThat(ToolPolicy.DENY_ALL.check("anything", Map.of()).denied()).isTrue();
    }

    @Test
    void decisionFactoryEnforcesNonNullReason() {
        try {
            ToolPolicy.Decision.deny(null);
            assertThat(false).as("expected NullPointerException").isTrue();
        }
        catch (NullPointerException expected) {
            // OK
        }
    }
}
