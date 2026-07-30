package zw.gov.mohcc.impilo.clinical.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.clinical.classification.ClassificationContentLoader;
import zw.gov.mohcc.impilo.clinical.maternal.MaternalNearMissService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The near-miss surface. Two things decide whether it is safe: an observation nobody recorded must not
 * read as an observation that was normal, and a figure computed over incomplete data must not read as
 * a complete one.
 */
class MaternalNearMissControllerTest {

    private MaternalNearMissController controller;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        controller = new MaternalNearMissController(
                new MaternalNearMissService(new ClassificationContentLoader(new ObjectMapper())));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ResponseEntity<Map<String, Object>> r) {
        return (Map<String, Object>) r.getBody().get("data");
    }

    private MaternalNearMissController.ObservationsRequest observations(String json) throws Exception {
        // Deserialised from the literal JSON a client sends, not built with a constructor. A
        // constructor call would let a field the client never sends be supplied here by accident, and
        // the absent-means-unrecorded property is exactly what these tests exist to prove.
        return mapper.readValue(json, MaternalNearMissController.ObservationsRequest.class);
    }

    // ── Classification ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a confirmed organ dysfunction is a near-miss and asks for a review")
    void confirmedNearMiss() throws Exception {
        var r = controller.classify(observations("""
                {"cardiacArrest": true}
                """));

        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(data(r).get("is_near_miss")).isEqualTo(true);
        assertThat(data(r).get("review_required")).isEqualTo(true);
        // Identification must point at the review without pretending to be one.
        assertThat((String) data(r).get("review_note")).contains("Identification is not a review");
        assertThat((String) data(r).get("review_note")).contains("MPDSR");
    }

    @Test
    @DisplayName("AN UNRECORDED OBSERVATION IS NOT A NORMAL ONE — it leaves the case unresolved")
    void silenceIsNotANegative() throws Exception {
        // Nothing was recorded at all.
        var nothing = controller.classify(observations("{}"));

        // If the boxed fields were primitives, every criterion would default to false/0 and this would
        // come back NEAR_MISS_NOT_MET: a woman who was never assessed, reported as a woman who was
        // assessed and found well. She would then be counted as a normal birth in the denominator.
        assertThat(nothing.getStatusCode().value()).isEqualTo(200);
        assertThat(data(nothing).get("is_near_miss")).isEqualTo(false);
        assertThat(data(nothing).get("classification_code"))
                .isNotEqualTo(MaternalNearMissService.NOT_MET);
        assertThat(String.valueOf(data(nothing).get("status"))).isEqualTo("INDETERMINATE");
    }

    @Test
    @DisplayName("an absent request body classifies as unassessed rather than 400")
    void absentBodyIsAClinicalState() {
        var r = controller.classify(null);

        // "This encounter recorded nothing relevant" is a real state and a real answer. A 400 would
        // make the caller invent a body, and the likeliest invention is all-false.
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(data(r).get("is_near_miss")).isEqualTo(false);
        assertThat(String.valueOf(data(r).get("status"))).isEqualTo("INDETERMINATE");
    }

    /** Every criterion recorded and every one normal — a woman who was fully assessed and is well. */
    private static final String FULLY_ASSESSED_AND_NORMAL = """
            {"shock": false, "cardiacArrest": false, "continuousVasoactiveDrugs": false,
             "cardiopulmonaryResuscitation": false, "lactateMmolL": 1.2, "phArterial": 7.38,
             "acuteCyanosis": false, "gasping": false, "respiratoryRate": 18,
             "intubationNotForAnaesthesiaMinutes": 0, "oxygenSaturationBelow90ForOverAnHour": false,
             "oliguriaNonResponsiveToFluids": false, "creatinineUmolL": 70,
             "dialysisForAcuteRenalFailure": false,
             "clottingFailure": false, "plateletsPerUl": 250000, "unitsTransfused": 0,
             "jaundiceWithPreEclampsia": false, "bilirubinUmolL": 12,
             "prolongedUnconsciousness": false, "stroke": false, "uncontrollableFits": false,
             "totalParalysis": false, "hysterectomyForInfectionOrHaemorrhage": false}
            """;

    @Test
    @DisplayName("a fully-assessed normal woman reaches not-met, unlike an unassessed one")
    void fullAssessmentDiffersFromAbsence() throws Exception {
        var absent = controller.classify(observations("{}"));
        var assessed = controller.classify(observations(FULLY_ASSESSED_AND_NORMAL));

        // Neither is a near-miss, but they are not the same finding and the surface must not flatten
        // them: one woman was assessed and found well, the other was never looked at.
        assertThat(data(absent).get("is_near_miss")).isEqualTo(false);
        assertThat(data(assessed).get("is_near_miss")).isEqualTo(false);
        assertThat(data(assessed).get("classification_code"))
                .isEqualTo(MaternalNearMissService.NOT_MET)
                .isNotEqualTo(data(absent).get("classification_code"));
    }

    @Test
    @DisplayName("denying every symptom is NOT enough to reach not-met while the labs are unrecorded")
    void deniedSymptomsWithoutLabsStayIndeterminate() throws Exception {
        var r = controller.classify(observations("""
                {"shock": false, "cardiacArrest": false, "continuousVasoactiveDrugs": false,
                 "cardiopulmonaryResuscitation": false, "acuteCyanosis": false, "gasping": false,
                 "oliguriaNonResponsiveToFluids": false, "dialysisForAcuteRenalFailure": false,
                 "clottingFailure": false, "jaundiceWithPreEclampsia": false,
                 "prolongedUnconsciousness": false, "stroke": false, "uncontrollableFits": false,
                 "totalParalysis": false, "hysterectomyForInfectionOrHaemorrhage": false}
                """));

        // Organ dysfunction is often silent: a woman can look well at the bedside and have a platelet
        // count of 30,000. Letting an all-clear on the observable signs close the case would let the
        // criteria that need a laboratory be answered by looking at her.
        assertThat(String.valueOf(data(r).get("status"))).isEqualTo("INDETERMINATE");
        assertThat(data(r).get("classification_code")).isNotEqualTo(MaternalNearMissService.NOT_MET);
    }

    @Test
    @DisplayName("the unresolved criteria are named, so the recording gap is actionable")
    void unresolvedCriteriaAreNamed() throws Exception {
        var r = controller.classify(observations("""
                {"shock": false}
                """));

        assertThat(data(r).get("provisional")).isNotNull();
        // A bare INDETERMINATE tells a clinician nothing to do. The open criteria are the work.
        assertThat(data(r)).containsKeys("unresolved_criteria", "missing_inputs", "rationale");
    }

    @Test
    @DisplayName("a threshold criterion fires on the value, not on a boolean the client computed")
    void thresholdCriteriaAreEvaluatedHere() throws Exception {
        var r = controller.classify(observations("""
                {"plateletsPerUl": 25000}
                """));

        // The client sends the measurement and the governed table decides. A client-side boolean would
        // pin the threshold into every app and drift from the ratified pack on the next revision.
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(data(r).get("is_near_miss")).isEqualTo(true);
    }

    // ── Indicators ─────────────────────────────────────────────────────────────────────────────────

    private static MaternalNearMissController.IndicatorRequest period(String label, String... outcomes) {
        return new MaternalNearMissController.IndicatorRequest(label,
                List.of(outcomes).stream()
                        .map(MaternalNearMissController.OutcomeCaseRequest::new)
                        .toList());
    }

    @Test
    @DisplayName("the ratio and index are returned with the period they were computed over")
    void indicatorsCarryTheirPeriod() {
        var r = controller.indicators(period("2026-Q1",
                "NEAR_MISS", "NEAR_MISS", "NEAR_MISS", "NEAR_MISS", "MATERNAL_DEATH", "NOT_SEVERE"));

        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(data(r).get("indicator_period")).isEqualTo("2026-Q1");
        assertThat(data(r).get("near_miss_to_death_ratio")).isEqualTo(4.0);
        assertThat(data(r).get("severe_maternal_outcome_count")).isEqualTo(5L);
        // Without the direction a consumer sorts ascending and calls the worst facility the best.
        assertThat(data(r).get("mortality_index_direction")).isEqualTo("HIGHER_IS_WORSE");
    }

    @Test
    @DisplayName("no deaths returns a null ratio, never an infinity and never a zero")
    void zeroDeathsIsNull() {
        var r = controller.indicators(period("2026-Q1", "NEAR_MISS", "NEAR_MISS"));

        // Double.POSITIVE_INFINITY is not valid JSON and 0 would read as the best possible ratio.
        assertThat(data(r).get("near_miss_to_death_ratio")).isNull();
        assertThat((String) data(r).get("note")).contains("not evidence that none will occur");
    }

    @Test
    @DisplayName("an unresolved survivor is reported and bounded, never counted as a near-miss")
    void indeterminateIsBoundedNotAbsorbed() {
        var r = controller.indicators(period("2026-Q1",
                "NEAR_MISS", "NEAR_MISS", "MATERNAL_DEATH",
                "NEAR_MISS_INDETERMINATE", "NEAR_MISS_INDETERMINATE"));

        assertThat(data(r).get("indeterminate_count")).isEqualTo(2L);
        // Absorbing the two into the near-miss count would take the index from 33% to 20% — an
        // unmeasured creatinine improving the facility's apparent quality of care.
        assertThat((Double) data(r).get("mortality_index")).isEqualTo(1.0 / 3);
        assertThat((Double) data(r).get("mortality_index_lower_bound")).isEqualTo(1.0 / 5);
        assertThat((String) data(r).get("note")).contains("flatter the service");
    }

    @Test
    @DisplayName("an unrecognised outcome is refused, not bucketed")
    void unrecognisedOutcomeIsRefused() {
        var r = controller.indicators(period("2026-Q1", "NEAR_MISS", "SEVERE_MAYBE"));

        // Guessing drops a woman out of the severe cohort or invents one. Both are silent; a 422 is not.
        assertThat(r.getStatusCode().value()).isEqualTo(422);
        assertThat(data(r).get("error")).isEqualTo("unclassifiable_outcome");
        assertThat((List<?>) data(r).get("rejected_cases")).hasSize(1);
        assertThat(String.valueOf(((List<?>) data(r).get("rejected_cases")).get(0)))
                .contains("case[1]").contains("SEVERE_MAYBE");
        assertThat(data(r)).containsKey("accepted_values");
    }

    @Test
    @DisplayName("an absent outcome value is refused with its position, not silently skipped")
    void absentOutcomeIsRefused() {
        var r = controller.indicators(new MaternalNearMissController.IndicatorRequest("2026-Q1",
                List.of(new MaternalNearMissController.OutcomeCaseRequest("NEAR_MISS"),
                        new MaternalNearMissController.OutcomeCaseRequest(null))));

        assertThat(r.getStatusCode().value()).isEqualTo(422);
        assertThat(String.valueOf(((List<?>) data(r).get("rejected_cases")).get(0)))
                .contains("case[1]").contains("<absent>");
    }

    @Test
    @DisplayName("outcome values are accepted case-insensitively, as clients actually send them")
    void outcomeParsingIsForgivingAboutCase() {
        var r = controller.indicators(period("2026-Q1", "near_miss", " MATERNAL_DEATH "));

        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(data(r).get("near_miss_count")).isEqualTo(1L);
        assertThat(data(r).get("maternal_death_count")).isEqualTo(1L);
    }

    @Test
    @DisplayName("an empty period is undefined, not a perfect score")
    void emptyPeriodIsUndefined() {
        var r = controller.indicators(period("2026-Q1"));

        assertThat(data(r).get("mortality_index")).isNull();
        assertThat((String) data(r).get("note")).contains("undefined, not zero");
        assertThat((String) data(r).get("note")).contains("not about the safety of this service");
    }
}
