package zw.gov.mohcc.impilo.clinical.rules.tabular;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * A clinical rule expressed as content: what it looks for, who it applies to, what must
 * happen when it fires, and where it came from.
 *
 * @param appliesWhen     an optional gate deciding whether the rule is about this patient at all,
 *                        separate from whether it fires. A postpartum rule is not "not firing" for
 *                        a woman who has not given birth — it does not apply to her. Evaluated with
 *                        the same three-valued semantics as {@code logic}: FALSE means the rule is
 *                        not applicable, UNKNOWN means the rule cannot fire but its required inputs
 *                        are reported unassessed, because an unknown gestational age must not
 *                        silently drop a gestation-scoped rule off the assessment
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
        JsonNode appliesWhen,
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

    /**
     * True when this rule's age window covers the patient.
     *
     * <p>A rule that declares <em>no</em> age window applies to everyone, including a patient whose
     * age is unknown — it never claimed to be about age. A rule that declares one and is given no
     * age matches nothing, because there is no way to tell whether the window covers them.
     *
     * <p>The distinction did not exist while every rule in the platform was paediatric and every
     * one of them declared a window. It matters now: contraceptive eligibility, Robson classification
     * and the maternal postnatal rules are scoped by gestation, parity or postpartum day, not by the
     * woman's age, and under the old behaviour every one of them would have silently failed to apply
     * to anyone.
     */
    public boolean appliesToAge(Integer ageDays) {
        if (ageMinDays == null && ageMaxDays == null) {
            return true;
        }
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
