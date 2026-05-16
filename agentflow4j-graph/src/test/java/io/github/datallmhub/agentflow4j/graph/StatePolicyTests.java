package io.github.datallmhub.agentflow4j.graph;

import io.github.datallmhub.agentflow4j.core.StateKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatePolicyTests {

    private static final StateKey<String>  DRAFT     = StateKey.of("ticket.draft",   String.class);
    private static final StateKey<Boolean> CONFIRMED = StateKey.of("payment.confirmed", Boolean.class);
    private static final StateKey<Double>  AMOUNT    = StateKey.of("payment.amount", Double.class);

    @Test
    void allowAllAndDenyAllBehaveAsAdvertised() {
        assertThat(StatePolicy.ALLOW_ALL.check(DRAFT, "hi").allowed()).isTrue();
        assertThat(StatePolicy.DENY_ALL.check(DRAFT, "hi").denied()).isTrue();
    }

    @Test
    void allowWriteKeysPermitsListedDeniesOthers() {
        StatePolicy policy = StatePolicy.allowWriteKeys("ticket.draft");

        assertThat(policy.check(DRAFT, "ok").allowed()).isTrue();
        StatePolicy.Decision denied = policy.check(CONFIRMED, true);
        assertThat(denied.denied()).isTrue();
        assertThat(denied.reason()).contains("payment.confirmed").contains("allow-list");
    }

    @Test
    void denyWriteKeysBlocksListedAllowsOthers() {
        StatePolicy policy = StatePolicy.denyWriteKeys("payment.confirmed");

        assertThat(policy.check(CONFIRMED, true).denied()).isTrue();
        assertThat(policy.check(DRAFT, "ok").allowed()).isTrue();
    }

    @Test
    void predicateRuleCanInspectKeyAndValue() {
        StatePolicy noHugePayments = StatePolicy.when(
                (key, value) -> !(key.name().equals("payment.amount")
                        && value instanceof Number n
                        && n.doubleValue() > 1000.0),
                "payment.amount above 1000 requires approval");

        assertThat(noHugePayments.check(AMOUNT, 500.0).allowed()).isTrue();
        StatePolicy.Decision denied = noHugePayments.check(AMOUNT, 5000.0);
        assertThat(denied.denied()).isTrue();
        assertThat(denied.reason()).isEqualTo("payment.amount above 1000 requires approval");
    }

    @Test
    void andComposesPoliciesAndShortCircuits() {
        StatePolicy first  = StatePolicy.allowWriteKeys("ticket.draft", "payment.confirmed");
        StatePolicy second = StatePolicy.denyWriteKeys("payment.confirmed");
        StatePolicy combined = first.and(second);

        assertThat(combined.check(DRAFT, "ok").allowed()).isTrue();
        assertThat(combined.check(CONFIRMED, true).denied()).isTrue();
        assertThat(combined.check(AMOUNT, 500.0).denied()).isTrue();
    }

    @Test
    void decisionFactoryRejectsNullReason() {
        try {
            StatePolicy.Decision.deny(null);
            assertThat(false).as("expected NullPointerException").isTrue();
        }
        catch (NullPointerException expected) {
            // OK
        }
    }
}
