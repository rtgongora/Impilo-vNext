package zw.gov.mohcc.impilo.tshepo.core;

import java.util.List;

/**
 * Immutable policy decision returned by the PolicyEngine.
 */
public record Decision(
    Verdict verdict,
    Obligations obligations,
    String denyReason,
    String denyMessage,
    List<String> stepUpMethods,
    int riskScore
) {
    public enum Verdict { ALLOW, DENY, STEP_UP_REQUIRED }

    public static Decision allow(Obligations obligations, int riskScore) {
        return new Decision(Verdict.ALLOW, obligations, null, null, null, riskScore);
    }

    public static Decision deny(String reason, String message, int riskScore) {
        return new Decision(Verdict.DENY, null, reason, message, null, riskScore);
    }

    public static Decision stepUpRequired(List<String> methods, int riskScore) {
        return new Decision(Verdict.STEP_UP_REQUIRED, null, null, null, methods, riskScore);
    }
}
