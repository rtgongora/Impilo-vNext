package zw.gov.mohcc.impilo.datagovernance.deid.transform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidDatasetEntity;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidReleasePolicyEntity;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidZone;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataUsePolicyGate — deny by default")
class DataUsePolicyGateTest {

    private static final UUID TENANT = UUID.randomUUID();

    private final DataUsePolicyGate gate = new DataUsePolicyGate();

    @Test
    @DisplayName("no policies at all => denied (deny-by-default)")
    void denyByDefault() {
        DataUsePolicyGate.GateDecision decision = gate.evaluate(dataset(null), List.of());
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo(DataUsePolicyGate.REASON_NO_ACTIVE_RELEASE_POLICY);
        assertThat(decision.policy()).isNull();
    }

    @Test
    @DisplayName("null candidate list => denied, never NPE")
    void nullCandidatesDenied() {
        DataUsePolicyGate.GateDecision decision = gate.evaluate(dataset(null), null);
        assertThat(decision.allowed()).isFalse();
    }

    @Test
    @DisplayName("DRAFT policy does not authorise a release")
    void draftPolicyDenied() {
        DeidReleasePolicyEntity draft = policy("pol-1", "RESEARCH", DeidReleasePolicyEntity.STATUS_DRAFT);
        DataUsePolicyGate.GateDecision decision = gate.evaluate(dataset(null), List.of(draft));
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo(DataUsePolicyGate.REASON_POLICY_NOT_ACTIVE);
    }

    @Test
    @DisplayName("ACTIVE policy with a different purpose is denied")
    void purposeMismatchDenied() {
        DeidReleasePolicyEntity active = policy("pol-1", "PUBLIC_HEALTH", DeidReleasePolicyEntity.STATUS_ACTIVE);
        DataUsePolicyGate.GateDecision decision = gate.evaluate(dataset(null), List.of(active));
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo(DataUsePolicyGate.REASON_POLICY_PURPOSE_MISMATCH);
    }

    @Test
    @DisplayName("ACTIVE policy matching the dataset purpose passes the gate")
    void activeMatchingPolicyPasses() {
        DeidReleasePolicyEntity active = policy("pol-1", "RESEARCH", DeidReleasePolicyEntity.STATUS_ACTIVE);
        DataUsePolicyGate.GateDecision decision = gate.evaluate(dataset(null), List.of(active));
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reasonCode()).isEqualTo(DataUsePolicyGate.REASON_POLICY_ACTIVE);
        assertThat(decision.policy()).isSameAs(active);
    }

    @Test
    @DisplayName("dataset pinned to a policy_ref only accepts that policy code")
    void pinnedPolicyRefIsEnforced() {
        DeidReleasePolicyEntity otherActive = policy("pol-other", "RESEARCH", DeidReleasePolicyEntity.STATUS_ACTIVE);
        DataUsePolicyGate.GateDecision denied = gate.evaluate(dataset("pol-pinned"), List.of(otherActive));
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.reasonCode()).isEqualTo(DataUsePolicyGate.REASON_NO_ACTIVE_RELEASE_POLICY);

        DeidReleasePolicyEntity pinned = policy("pol-pinned", "RESEARCH", DeidReleasePolicyEntity.STATUS_ACTIVE);
        DataUsePolicyGate.GateDecision allowed = gate.evaluate(dataset("pol-pinned"),
                List.of(otherActive, pinned));
        assertThat(allowed.allowed()).isTrue();
        assertThat(allowed.policy()).isSameAs(pinned);
    }

    private static DeidDatasetEntity dataset(String policyRef) {
        return new DeidDatasetEntity(TENANT, "ds-" + UUID.randomUUID(), "Test dataset",
                "RESEARCH", DeidZone.RELEASED, policyRef, "secret-ref");
    }

    private static DeidReleasePolicyEntity policy(String code, String purpose, String status) {
        DeidReleasePolicyEntity policy = new DeidReleasePolicyEntity(TENANT, code, purpose);
        policy.setStatus(status);
        return policy;
    }
}
