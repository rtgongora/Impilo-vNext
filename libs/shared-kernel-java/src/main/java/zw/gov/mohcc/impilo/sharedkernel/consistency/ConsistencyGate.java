package zw.gov.mohcc.impilo.sharedkernel.consistency;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates whether a given action requires synchronous confirmation
 * from a federation authority before it may proceed.
 * <p>
 * Initial mapping is hardcoded per the v1.1 companion spec;
 * future versions may load rules from a configuration store.
 */
public final class ConsistencyGate {

    private static final Map<ActionType, ConsistencyDecision> RULES;

    static {
        RULES = new EnumMap<>(ActionType.class);
        RULES.put(ActionType.MSIKA_UPDATE_TARIFF,
                new ConsistencyDecision(true, "NATIONAL_SPINE_ONLY"));
        RULES.put(ActionType.VITO_PATIENT_MERGE,
                new ConsistencyDecision(true, "NATIONAL_SPINE_ONLY"));
        RULES.put(ActionType.CLINICAL_PRESCRIBE_CONTROLLED_SUBSTANCE,
                new ConsistencyDecision(true, "KERNEL_PDP_REQUIRED"));
        RULES.put(ActionType.UNKNOWN,
                new ConsistencyDecision(false, "PROJECTION_OK"));
    }

    /**
     * Evaluate whether the action requires synchronous spine authority.
     *
     * @param actionType the action being attempted
     * @param podId      the pod originating the request (reserved for future zone-aware rules)
     * @return a {@link ConsistencyDecision} describing sync requirements and authority
     * @throws NullPointerException if actionType is null
     */
    public ConsistencyDecision evaluate(ActionType actionType, String podId) {
        Objects.requireNonNull(actionType, "actionType must not be null");
        return RULES.getOrDefault(actionType,
                new ConsistencyDecision(false, "PROJECTION_OK"));
    }
}
