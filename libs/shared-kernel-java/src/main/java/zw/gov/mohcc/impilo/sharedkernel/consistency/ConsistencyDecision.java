package zw.gov.mohcc.impilo.sharedkernel.consistency;

/**
 * Result of a consistency gate evaluation.
 *
 * @param syncRequired true when the action must be confirmed by the authority before proceeding
 * @param authority    the governance authority that owns the decision (e.g. NATIONAL_SPINE_ONLY)
 */
public record ConsistencyDecision(boolean syncRequired, String authority) {
}
