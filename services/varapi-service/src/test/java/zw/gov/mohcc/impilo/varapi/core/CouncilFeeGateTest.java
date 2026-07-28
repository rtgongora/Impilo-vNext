package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The fee gate's one job: never let an unset fee read as permission.
 *
 * <p>These test the DECISION, not the query — the SQL half is proven against a real database by
 * {@code scripts/runtime-proof/ncz-student-registration-invariants.sql}. What is asserted here is
 * the thing a future edit would most plausibly break: that {@code NOT_CONFIGURED} is treated as a
 * refusal rather than as "PAYABLE with a missing number".</p>
 *
 * <p>This proof was deferred across two waves and is now paid. It is written to FAIL if the gate is
 * loosened — see the comment on {@link #theOnlyWayPastTheGateIsAConfiguredFee()}.</p>
 */
class CouncilFeeGateTest {

    private static CouncilFeeGate.FeeVerdict payable() {
        return new CouncilFeeGate.FeeVerdict(
                CouncilFeeGate.Verdict.PAYABLE, new BigDecimal("25.00"), "USD", null);
    }

    private static CouncilFeeGate.FeeVerdict notConfigured() {
        return new CouncilFeeGate.FeeVerdict(
                CouncilFeeGate.Verdict.NOT_CONFIGURED, null, null, "NCZ-DEC-002");
    }

    private static CouncilFeeGate.FeeVerdict noSuchFee() {
        return new CouncilFeeGate.FeeVerdict(
                CouncilFeeGate.Verdict.NO_SUCH_FEE, null, null, null);
    }

    @Test
    void theOnlyWayPastTheGateIsAConfiguredFee() {
        // If someone later widens allowsChargeToProceed() — to accept NOT_CONFIGURED, or to reason
        // from the amount being null — this is the assertion that goes red. It is the whole reason
        // the method exists rather than callers comparing the enum themselves.
        assertThat(payable().allowsChargeToProceed()).isTrue();
        assertThat(notConfigured().allowsChargeToProceed()).isFalse();
        assertThat(noSuchFee().allowsChargeToProceed()).isFalse();
    }

    @Test
    void anUnsetFeeIsNeverDescribedAsFree() {
        // The failure mode is not a crash, it is a sentence. "No fee payable" on a screen is how an
        // applicant learns they owe nothing, and it must never appear because a value was absent.
        String explanation = notConfigured().explain();

        assertThat(explanation).contains("has not been set by the Council");
        assertThat(explanation).contains("NCZ-DEC-002");
        assertThat(explanation.toLowerCase()).doesNotContain("free");
        assertThat(explanation).doesNotContain("0.00");
    }

    @Test
    void anUnsetFeeIsDistinguishableFromAFeeThatDoesNotExist() {
        // Collapsing these two would hide a configuration mistake behind a legitimate-looking
        // answer: "no such fee" is a coding error; "not configured" is a Council decision pending.
        assertThat(notConfigured().verdict()).isNotEqualTo(noSuchFee().verdict());
        assertThat(noSuchFee().explain()).doesNotContain("Council");
    }

    @Test
    void requirePayableRefusesAnUnsetFeeAndSaysWhichDecisionIsOutstanding() {
        CouncilFeeGate gate = new CouncilFeeGate() {
            @Override
            public FeeVerdict verdictFor(java.util.UUID t, Long c, String code) {
                return notConfigured();
            }
        };

        assertThatThrownBy(() -> gate.requirePayable(
                java.util.UUID.randomUUID(), 1L, "STUDENT_INDEX"))
                .isInstanceOf(CouncilFeeGate.FeeNotChargeableException.class)
                .hasMessageContaining("STUDENT_INDEX")
                .hasMessageContaining("NCZ-DEC-002");
    }

    @Test
    void requirePayableLetsAConfiguredFeeThrough() {
        CouncilFeeGate gate = new CouncilFeeGate() {
            @Override
            public FeeVerdict verdictFor(java.util.UUID t, Long c, String code) {
                return payable();
            }
        };

        CouncilFeeGate.FeeVerdict verdict =
                gate.requirePayable(java.util.UUID.randomUUID(), 1L, "STUDENT_INDEX");

        assertThat(verdict.amount()).isEqualByComparingTo("25.00");
        assertThat(verdict.currency()).isEqualTo("USD");
    }

    @Test
    void aPayableVerdictAlwaysCarriesAnAmountAndCurrency() {
        // PAYABLE with a null amount would be the same silent failure wearing a different label.
        CouncilFeeGate.FeeVerdict verdict = payable();
        assertThat(verdict.amount()).isNotNull();
        assertThat(verdict.currency()).isNotNull();
        assertThat(verdict.explain()).contains("25.00").contains("USD");
    }
}
