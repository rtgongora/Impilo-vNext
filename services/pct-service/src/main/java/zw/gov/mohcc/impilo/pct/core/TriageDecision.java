package zw.gov.mohcc.impilo.pct.core;

import java.util.List;
import java.util.Map;

/**
 * The outcome of {@link EmergencyTriageDecider#decide}. Either a triage ({@code triageable} true,
 * carrying an acuity and the tool that set it) or a refusal ({@code triageable} false, carrying a
 * machine-readable {@code refusalCode} and the inputs that were not assessed). A refusal is never
 * silently converted into a number — it is returned to the caller as a 422 so the assessment is
 * completed.
 *
 * @param triageable              whether a triage priority was assigned
 * @param acuity                  1-5 acuity of record, or null when not triageable
 * @param triageTool              WHO_IITT (authority) or ESI/MTS/IMPILO_5 (a manual acuity)
 * @param iittPriority            RED/YELLOW/GREEN, or null on a manual row
 * @param chart                   which IITT chart applied, echoed for the audit trail; null if manual
 * @param triggeringFindings      the IITT criteria that fired, in the chart's wording
 * @param highRiskVitalSigns      IITT step-3 findings when no red/yellow criterion fired
 * @param unassessedInputs        what was not assessed — the reason a GREEN was withheld or a refusal raised
 * @param requiresClinicianReview IITT up-triage / cross-system discrepancy flag for a human
 * @param reviewReason            why review is required, for the record; null when not required
 * @param emergencySigns          the signs recorded on this assessment, echoed for persistence
 * @param advisoryScores          ESI/MTS advisory scores, never the acuity of record; may be null
 * @param refusalCode             machine code when not triageable (age_required, not_triageable); else null
 * @param refusalMessage          human message when not triageable; else null
 */
public record TriageDecision(
        boolean triageable,
        Integer acuity,
        String triageTool,
        String iittPriority,
        String chart,
        List<String> triggeringFindings,
        List<String> highRiskVitalSigns,
        List<String> unassessedInputs,
        boolean requiresClinicianReview,
        String reviewReason,
        Map<String, Object> emergencySigns,
        Map<String, Object> advisoryScores,
        String refusalCode,
        String refusalMessage) {

    static TriageDecision refused(String code, String message, List<String> unassessedInputs) {
        return new TriageDecision(false, null, null, null, null, List.of(), List.of(),
                unassessedInputs == null ? List.of() : unassessedInputs, false, null,
                Map.of(), null, code, message);
    }
}
