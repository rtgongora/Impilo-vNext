package zw.gov.mohcc.impilo.clinical.rules.tabular;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * A clinical rule expressed as content: what it looks for, who it applies to, what must
 * happen when it fires, and where it came from.
 *
 * @param requiredInputs  the observations the rule needs; used to say what could not be
 *                        assessed rather than letting silence read as a negative finding
 * @param approvalStatus  ENGINEERING_SEED content is implemented and testable but not ratified
 */
public record TabularRule(
        String code,
        String name,
        String layer,
        String ruleType,
        String severity,
        boolean interruptive,
        boolean overrideAllowed,
        boolean referralRequired,
        Integer ageMinDays,
        Integer ageMaxDays,
        List<String> requiredInputs,
        JsonNode logic,
        String message,
        String explanation,
        String requiredAction,
        String monitoringInstruction,
        List<String> sourceRefs,
        String approvalStatus,
        String adaptationAuthority,
        String contentVersion,
        List<TestCase> testCases) {

    /** True when this rule's age window covers the patient. Unknown age matches nothing. */
    public boolean appliesToAge(Integer ageDays) {
        if (ageDays == null) {
            return false;
        }
        if (ageMinDays != null && ageDays < ageMinDays) {
            return false;
        }
        return ageMaxDays == null || ageDays <= ageMaxDays;
    }

    public boolean ratified() {
        return "APPROVED".equalsIgnoreCase(approvalStatus);
    }

    /**
     * A fixture that ships with the rule. A rule whose own fixtures do not pass cannot be
     * released, which is what stops content edits from silently changing clinical behaviour.
     *
     * @param expectIncomplete the rule should report unassessed inputs rather than firing
     */
    public record TestCase(
            String name,
            java.util.Map<String, Object> facts,
            boolean expectFires,
            boolean expectIncomplete) {
    }
}
