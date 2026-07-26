package zw.gov.mohcc.impilo.clinical.classification;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * One IMNCI assess-and-classify table.
 *
 * <p>A classification table is not a list of independent rules. Its rows are ordered by severity
 * and exactly one of them applies: a child with cough is classified as severe pneumonia
 * <em>or</em> pneumonia <em>or</em> cough-or-cold, never two of them. That ordering is the
 * clinical content, not an implementation detail, so it is preserved in the content file and the
 * engine walks it from the most severe row down.</p>
 *
 * <p>A classification is not a diagnosis. IMNCI classifies in order to select a treatment and a
 * disposition using signs a health worker can elicit without investigations; it deliberately
 * groups conditions that need the same immediate action. "Severe pneumonia or very severe disease"
 * is a management category covering several diseases, and recording it as a diagnosis would put a
 * label in the record that no clinician ever asserted.</p>
 *
 * @param axis          what this table decides, e.g. dehydration status — a symptom can require
 *                      several tables (diarrhoea yields dehydration, persistence and dysentery
 *                      as three independent answers)
 * @param appliesWhen   the screening predicate; when it does not hold the table is not applicable,
 *                      and when it cannot be evaluated the table is unassessed rather than absent
 * @param tiers         ordered most severe first
 */
public record ClassificationTable(
        String tableId,
        String name,
        String axis,
        Integer ageMinDays,
        Integer ageMaxDays,
        JsonNode appliesWhen,
        List<String> requiredInputs,
        List<Tier> tiers,
        List<String> sourceRefs,
        List<TestCase> testCases) {

    /**
     * True when this table's age window covers the patient.
     *
     * <p>A table that declares no age window applies to everyone, including a patient of unknown
     * age — it never claimed to be about age. One that declares a window and is given no age matches
     * nothing, because there is no way to tell whether the window covers them. Robson classification
     * is the case that forced the distinction: its ten groups turn on parity, plurality,
     * presentation, gestation and onset of labour, and never on how old the woman is.
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

    /**
     * One row of the table.
     *
     * @param colour  IMNCI's triage colour: PINK is urgent referral, YELLOW treat at this
     *                facility, GREEN home care with advice. The colour drives the disposition,
     *                which is why it is content rather than presentation.
     * @param logic   null on the default row, which is reached only when every row above it has
     *                been definitively excluded
     */
    public record Tier(
            String code,
            String name,
            String colour,
            JsonNode logic,
            String action,
            List<String> treatments,
            boolean referralRequired,
            boolean urgentReferral) {

        public boolean isDefault() {
            return logic == null || logic.isNull();
        }
    }

    /**
     * A fixture shipped with the table and executed as a build gate, so a content edit cannot
     * silently change which children are classified as severely ill.
     *
     * @param expectClassification the tier code expected, or null when the table should not
     *                             classify at all
     * @param expectProvisional    the classification must be marked provisional because a more
     *                             severe row could not be excluded
     */
    public record TestCase(
            String name,
            Map<String, Object> facts,
            String expectStatus,
            String expectClassification,
            boolean expectProvisional) {
    }
}
