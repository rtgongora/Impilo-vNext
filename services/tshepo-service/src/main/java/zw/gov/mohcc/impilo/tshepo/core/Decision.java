package zw.gov.mohcc.impilo.tshepo.core;

import java.util.List;

/**
 * Immutable policy decision returned by the PolicyEngine.
 *
 * <p>Every decision includes evidence fields for audit trail compliance:
 * <ul>
 *   <li>{@code policyVersion} — version of the policy rules that produced this decision</li>
 *   <li>{@code reasonCodes} — machine-readable codes explaining the decision</li>
 * </ul>
 */
public record Decision(
    Verdict verdict,
    Obligations obligations,
    String denyReason,
    String denyMessage,
    List<String> stepUpMethods,
    int riskScore,
    String policyVersion,
    List<String> reasonCodes
) {
    public enum Verdict { ALLOW, DENY, STEP_UP_REQUIRED }

    public static final String CURRENT_POLICY_VERSION = "v1.1.0";

    public static Decision allow(Obligations obligations, int riskScore) {
        return new Decision(Verdict.ALLOW, obligations, null, null, null, riskScore,
                CURRENT_POLICY_VERSION, List.of("POLICY_SATISFIED"));
    }

    public static Decision allow(Obligations obligations, int riskScore, List<String> reasonCodes) {
        return new Decision(Verdict.ALLOW, obligations, null, null, null, riskScore,
                CURRENT_POLICY_VERSION, reasonCodes);
    }

    public static Decision deny(String reason, String message, int riskScore) {
        return new Decision(Verdict.DENY, null, reason, message, null, riskScore,
                CURRENT_POLICY_VERSION, List.of(reason));
    }

    public static Decision stepUpRequired(List<String> methods, int riskScore) {
        return new Decision(Verdict.STEP_UP_REQUIRED, null, null, null, methods, riskScore,
                CURRENT_POLICY_VERSION, List.of("STEP_UP_REQUIRED"));
    }
}
