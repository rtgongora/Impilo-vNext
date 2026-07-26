package zw.gov.mohcc.impilo.emergency.triage;

import java.util.List;
import java.util.function.Predicate;

/**
 * One criterion from a published IITT chart.
 *
 * <p>Each carries the chart heading it sits under and the exact printed label, so a fired criterion
 * can be shown to a clinician in the chart's own words and traced back to
 * {@code docs/reference/who-emergency-care-toolkit/} — where the source PDFs are vendored and
 * hashed, because none of them carries a version number.
 *
 * @param code          stable identifier; append-only, never renumbered, because override records
 *                      and recommendation traces reference it forever
 * @param tier          RED or YELLOW (GREEN is the absence of both, not a criterion)
 * @param group         the chart heading: AIRWAY_BREATHING, CIRCULATION, DISABILITY, OTHER,
 *                      PREGNANT, or GENERAL for the ungrouped "Unresponsive"
 * @param label         the printed wording, verbatim
 * @param requiredSigns the sign codes this criterion reads; used to report what could not be
 *                      assessed rather than letting silence read as absence
 * @param test          evaluates the criterion against assessed facts
 */
public record IittCriterion(
        String code,
        TriagePriority tier,
        String group,
        String label,
        List<String> requiredSigns,
        Predicate<TriageFacts> test) {

    /** True when every sign this criterion reads was explicitly assessed. */
    public boolean fullyAssessed(TriageFacts facts) {
        return requiredSigns.stream().allMatch(s -> facts.sign(s) != TriageFacts.Ternary.UNKNOWN);
    }

    public boolean fires(TriageFacts facts) {
        return test.test(facts);
    }
}
