package zw.gov.mohcc.impilo.datagovernance.deid.transform;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidDatasetEntity;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidReleasePolicyEntity;

import java.util.List;
import java.util.Objects;

/**
 * Data-use policy gate (design principle 5) — deny by default.
 *
 * <p>A release requires an {@code ACTIVE} {@code deid_release_policy} whose
 * purpose matches the dataset purpose; when the dataset pins a
 * {@code policy_ref}, only that policy code is considered. No matching active
 * policy → the run is {@code REJECTED} with the deny reason recorded.</p>
 */
@Component
public class DataUsePolicyGate {

    public static final String REASON_POLICY_ACTIVE = "POLICY_ACTIVE";
    public static final String REASON_NO_ACTIVE_RELEASE_POLICY = "NO_ACTIVE_RELEASE_POLICY";
    public static final String REASON_POLICY_NOT_ACTIVE = "POLICY_NOT_ACTIVE";
    public static final String REASON_POLICY_PURPOSE_MISMATCH = "POLICY_PURPOSE_MISMATCH";

    /** Gate decision; {@code policy} is non-null only when {@code allowed}. */
    public record GateDecision(boolean allowed, String reasonCode, DeidReleasePolicyEntity policy) {

        static GateDecision allow(DeidReleasePolicyEntity policy) {
            return new GateDecision(true, REASON_POLICY_ACTIVE, policy);
        }

        static GateDecision deny(String reasonCode) {
            return new GateDecision(false, reasonCode, null);
        }
    }

    public GateDecision evaluate(DeidDatasetEntity dataset,
                                 List<DeidReleasePolicyEntity> candidatePolicies) {
        List<DeidReleasePolicyEntity> candidates = candidatePolicies == null
                ? List.of()
                : candidatePolicies.stream()
                        .filter(p -> dataset.getPolicyRef() == null
                                || Objects.equals(dataset.getPolicyRef(), p.getPolicyCode()))
                        .toList();

        if (candidates.isEmpty()) {
            return GateDecision.deny(REASON_NO_ACTIVE_RELEASE_POLICY);
        }

        boolean anyActive = false;
        for (DeidReleasePolicyEntity candidate : candidates) {
            if (!candidate.isActive()) {
                continue;
            }
            anyActive = true;
            if (Objects.equals(candidate.getPurpose(), dataset.getPurpose())) {
                return GateDecision.allow(candidate);
            }
        }
        return GateDecision.deny(anyActive
                ? REASON_POLICY_PURPOSE_MISMATCH
                : REASON_POLICY_NOT_ACTIVE);
    }
}
