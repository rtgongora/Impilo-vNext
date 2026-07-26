package zw.gov.mohcc.impilo.emergency.triage;

import java.util.List;

/**
 * The outcome of one IITT evaluation.
 *
 * @param priority              the tier, or NOT_TRIAGEABLE when the assessment cannot support one
 * @param chart                 which chart was applied, echoed so a retrospective reader knows
 *                              whether the adult or paediatric criteria produced this
 * @param triggeringCriteria    the criteria that fired, in the chart's own wording
 * @param highRiskVitalSigns    step-3 findings, populated only when no red or yellow criterion fired
 * @param unassessedInputs      what was never assessed — the reason a GREEN was withheld, and the
 *                              list a clinician needs in order to complete the triage
 * @param requiresClinicianReview true when the chart's step-3 instruction applies: "up-triage or
 *                              immediate review by supervising clinician"
 */
public record TriageResult(
        TriagePriority priority,
        String chart,
        List<IittCriterion> triggeringCriteria,
        List<String> highRiskVitalSigns,
        List<String> unassessedInputs,
        boolean requiresClinicianReview) {

    /** Where the chart says this patient goes. */
    public String destination() {
        return priority.destination();
    }

    /** The fired criteria as printed labels, for display and for the audit trail. */
    public List<String> triggeringFindings() {
        return triggeringCriteria.stream().map(IittCriterion::label).toList();
    }

    /** True when this result is a positive triage decision rather than a withheld one. */
    public boolean isTriaged() {
        return priority != TriagePriority.NOT_TRIAGEABLE;
    }
}
