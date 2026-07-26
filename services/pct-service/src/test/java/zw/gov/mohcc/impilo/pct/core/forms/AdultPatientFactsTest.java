package zw.gov.mohcc.impilo.pct.core.forms;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The adult clinical facts, and the three states each of them has.
 *
 * <p>Renal dosing, hepatic dosing, interaction checking and deprescribing were unimplementable
 * rather than unimplemented, because the facts they need could not reach an engine at all. What
 * matters about the fix is not that the numbers now travel — it is that <em>absent</em>,
 * <em>present</em> and <em>present but stale</em> stay three distinguishable things all the way
 * to the rule.</p>
 */
class AdultPatientFactsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 26);

    private static PatientFacts adult() {
        return new PatientFacts(480, "MALE", false, List.of(), List.of(), null, null);
    }

    // ── Absent is never normal ──────────────────────────────────────────────────

    /**
     * The property the whole design turns on. A rule that needs an eGFR and does not have one must
     * decline to score and say so; it must never see a zero. A zero eGFR is dialysis-grade renal
     * failure and would silently produce the most aggressive dose reduction in the book.
     */
    @Test
    void anUnmeasuredFactIsAbsentFromTheRuleFactsRatherThanZero() {
        Map<String, Object> facts = adult().toRuleFacts(TODAY);

        assertThat(facts).doesNotContainKey("egfr");
        assertThat(facts).doesNotContainKey("weightKg");
        assertThat(facts).doesNotContainKey("hepaticImpairment");
        assertThat(facts.values()).doesNotContainNull();
    }

    @Test
    void unknownFactsReportThemselvesAsUnknownRatherThanNormal() {
        assertThat(PatientFacts.unknown().renalFunctionOrUnknown().known()).isFalse();
        assertThat(PatientFacts.unknown().weightOrUnknown().known()).isFalse();
        assertThat(PatientFacts.unknown().hepaticImpairment()).isNull();
        assertThat(PatientFacts.unknown().currentMedicationCodesOrEmpty()).isEmpty();
    }

    // ── Present, with when it was true ──────────────────────────────────────────

    @Test
    void carriesTheAdultFactsAnEngineNeedsToDoseSafely() {
        PatientFacts facts = adult().withClinicalFacts(
                DatedMeasurement.of(72.5, "kg", TODAY.minusDays(3), "MEASURED"),
                DatedMeasurement.of(38.0, "mL/min/1.73m2", TODAY.minusDays(10), "CKD-EPI"),
                "MODERATE",
                List.of("J01CA04", "C09AA05"));

        Map<String, Object> rules = facts.toRuleFacts(TODAY);
        assertThat(rules).containsEntry("weightKg", 72.5);
        assertThat(rules).containsEntry("egfr", 38.0);
        assertThat(rules).containsEntry("egfrEquation", "CKD-EPI");
        assertThat(rules).containsEntry("hepaticImpairment", "MODERATE");
        assertThat(rules).containsEntry("currentMedicationCodes", List.of("J01CA04", "C09AA05"));
    }

    /** The rule decides how old is too old, so the fact has to say how old it is. */
    @Test
    void reportsHowOldEachMeasurementIsSoAGovernedRuleCanJudgeIt() {
        PatientFacts facts = adult().withClinicalFacts(
                DatedMeasurement.of(70.0, "kg", TODAY.minusDays(400), "MEASURED"),
                DatedMeasurement.of(55.0, "mL/min/1.73m2", TODAY.minusDays(2), "CKD-EPI"),
                null, null);

        Map<String, Object> rules = facts.toRuleFacts(TODAY);
        assertThat(rules).containsEntry("weightMeasuredDaysAgo", 400L);
        assertThat(rules).containsEntry("egfrMeasuredDaysAgo", 2L);
    }

    // ── Present but undateable — the dangerous middle state ─────────────────────

    /**
     * A value whose date nobody recorded must not read as taken today. Zero days old is the most
     * reassuring answer available and the one least supported by the record, and unlike a missing
     * value a stale one looks entirely usable — so nothing prompts anyone to measure again.
     */
    @Test
    void aMeasurementWithNoDateDoesNotClaimToBeFresh() {
        DatedMeasurement undated = DatedMeasurement.of(80.0, "kg", null, "STATED");

        assertThat(undated.known()).isTrue();
        assertThat(undated.ageInDays(TODAY)).isNull();
        assertThat(undated.olderThan(30, TODAY)).isNull();

        Map<String, Object> rules = adult()
                .withClinicalFacts(undated, null, null, null).toRuleFacts(TODAY);
        assertThat(rules).containsEntry("weightKg", 80.0);
        assertThat(rules).doesNotContainKey("weightMeasuredDaysAgo");
    }

    /** Three-valued on purpose: "we don't know how old this is" is not "it is fresh". */
    @Test
    void stalenessIsThreeValued() {
        assertThat(DatedMeasurement.of(1.0, "kg", TODAY.minusDays(90), "MEASURED")
                .olderThan(30, TODAY)).isTrue();
        assertThat(DatedMeasurement.of(1.0, "kg", TODAY.minusDays(5), "MEASURED")
                .olderThan(30, TODAY)).isFalse();
        assertThat(DatedMeasurement.unknown().olderThan(30, TODAY)).isNull();
    }

    // ── Never overload a shared fact key ────────────────────────────────────────

    /**
     * The facts map is shared by every rule in one evaluation, so overloading a key corrupts every
     * other rule in the same request. A fact named ageDays holding a gestation or a weight reads as
     * an infant to the dosing engine, and the dosing engine will not say that it did.
     */
    @Test
    void eachFactHasItsOwnKeyAndNothingIsFoldedIntoAgeDays() {
        PatientFacts facts = new PatientFacts(480, "FEMALE", null, List.of(), List.of(), 17520, 39)
                .withClinicalFacts(
                        DatedMeasurement.of(64.0, "kg", TODAY, "MEASURED"),
                        DatedMeasurement.of(90.0, "mL/min/1.73m2", TODAY, "CKD-EPI"),
                        "NONE", List.of("A10BA02"));

        Map<String, Object> rules = facts.toRuleFacts(TODAY);
        assertThat(rules).containsEntry("ageDays", 17520);
        assertThat(rules).containsEntry("gestationalAgeWeeks", 39);
        assertThat(rules).containsEntry("weightKg", 64.0);
        assertThat(rules).containsEntry("egfr", 90.0);
        assertThat(rules.keySet()).doesNotHaveDuplicates();
    }

    /** An unknown sex is not a sex, and a rule filtering on it would silently exclude the patient. */
    @Test
    void anUnknownSexIsOmittedRatherThanPassedAsTheStringUnknown() {
        assertThat(PatientFacts.unknown().toRuleFacts(TODAY)).doesNotContainKey("sex");
        assertThat(adult().toRuleFacts(TODAY)).containsEntry("sex", "MALE");
    }

    // ── The existing callers keep working ───────────────────────────────────────

    @Test
    void theConvenienceConstructorsStillBuildValidFacts() {
        PatientFacts five = new PatientFacts(24, "MALE", false, List.of(), List.of());
        assertThat(five.ageDays()).isNull();
        assertThat(five.weight()).isNull();

        PatientFacts seven = new PatientFacts(2, "FEMALE", null, List.of(), List.of(), 60, 34);
        assertThat(seven.ageDays()).isEqualTo(60);
        assertThat(seven.gestationalAgeWeeks()).isEqualTo(34);
        assertThat(seven.renalFunction()).isNull();
    }
}
