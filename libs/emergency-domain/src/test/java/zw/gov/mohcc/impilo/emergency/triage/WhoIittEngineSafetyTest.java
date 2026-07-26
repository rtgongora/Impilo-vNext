package zw.gov.mohcc.impilo.emergency.triage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The properties that make this engine safe to replace the one before it.
 *
 * <p>The engine being replaced failed in two directions at once: {@code intVal()} returned {@code 0}
 * for an unmeasured vital while every comparison was guarded with {@code hr > 0 &&}, so an
 * unmeasured patient scored as not-in-danger; and a patient with no triage data at all was recorded
 * as acuity 3. Both are the same mistake — <b>absence rendered as a reassuring value</b> — and both
 * are asserted against here.
 */
class WhoIittEngineSafetyTest {

    @Test
    @DisplayName("a patient nobody assessed is NOT_TRIAGEABLE, never GREEN")
    void unassessedPatientIsNeverGreen() {
        TriageFacts nothing = TriageFacts.builder().ageDays(30 * 365).build();

        TriageResult r = WhoIittEngine.evaluate(nothing);

        assertEquals(TriagePriority.NOT_TRIAGEABLE, r.priority());
        assertNotEquals(TriagePriority.GREEN, r.priority(),
                "GREEN is a positive claim that everything was checked and nothing found");
        assertTrue(r.unassessedInputs().contains(IittChart.HR), "must say what was not assessed");
        assertTrue(r.unassessedInputs().contains(IittChart.SPO2));
        assertTrue(r.unassessedInputs().contains(IittChart.AVPU));
        assertTrue(r.destination().contains("Insufficient assessment"));
    }

    @Test
    @DisplayName("an unmeasured vital sign never counts as a normal one")
    void unmeasuredVitalsAreNotNormalVitals() {
        // Everything assessed and normal EXCEPT SpO2, which was never measured.
        TriageFacts.Builder b = allSignsAbsent(30 * 365)
                .vital(IittChart.HR, 80).vital(IittChart.RR, 16)
                .vital(IittChart.TEMP, 37.0).vital(IittChart.AVPU, 0);

        TriageResult r = WhoIittEngine.evaluate(b.build());

        assertEquals(TriagePriority.NOT_TRIAGEABLE, r.priority(),
                "one unmeasured step-3 vital is enough to withhold GREEN");
        assertTrue(r.unassessedInputs().contains(IittChart.SPO2));
        assertTrue(r.highRiskVitalSigns().isEmpty(),
                "an unmeasured SpO2 must not be reported as a high-risk finding either — "
                        + "it is unknown, not abnormal");
    }

    @Test
    @DisplayName("a fully assessed well patient does reach GREEN")
    void fullyAssessedWellPatientIsGreen() {
        TriageResult r = WhoIittEngine.evaluate(completeNormalAdult().build());

        assertEquals(TriagePriority.GREEN, r.priority());
        assertTrue(r.unassessedInputs().isEmpty());
        assertTrue(r.destination().contains("low acuity"));
    }

    @Test
    @DisplayName("red beats yellow — step order is not an implementation detail")
    void redBeatsYellow() {
        TriageFacts both = completeNormalAdult()
                .sign(IittChart.STRIDOR, true)            // red
                .sign(IittChart.WHEEZING, true)           // yellow
                .build();

        TriageResult r = WhoIittEngine.evaluate(both);

        assertEquals(TriagePriority.RED, r.priority());
        assertTrue(r.triggeringCriteria().stream().allMatch(c -> c.tier() == TriagePriority.RED),
                "a red result must report red criteria, not a mix");
    }

    @Test
    @DisplayName("unknown age raises rather than guessing a chart")
    void unknownAgeIsRefused() {
        TriageFacts noAge = TriageFacts.builder().vital(IittChart.HR, 80).build();

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> WhoIittEngine.evaluate(noAge));
        assertTrue(e.getMessage().contains("no safe default chart"));
    }

    @Test
    @DisplayName("age selects the chart, and the charts disagree")
    void ageSelectsTheChart() {
        assertEquals("IITT_ADULT_AGE_12_AND_OVER",
                WhoIittEngine.evaluate(completeNormalAdult().build()).chart());
        assertEquals("IITT_PAEDIATRIC_AGE_UNDER_12",
                WhoIittEngine.evaluate(allSignsAbsent(3 * 365)
                        .vital(IittChart.HR, 110).vital(IittChart.RR, 24)
                        .vital(IittChart.TEMP, 37.0).vital(IittChart.SPO2, 98)
                        .vital(IittChart.AVPU, 0).build()).chart());
    }

    /**
     * Same heart rate, different answer, because the bands are age-specific. 150 is a step-3
     * high-risk finding for a 10-year-old and unremarkable for an infant — using one band for both
     * is the defect this replaces in a different guise.
     */
    @Test
    @DisplayName("paediatric vital bands are age-specific")
    void paediatricBandsAreAgeSpecific() {
        TriageResult infant = WhoIittEngine.evaluate(allSignsAbsent(180)
                .vital(IittChart.HR, 150).vital(IittChart.RR, 40)
                .vital(IittChart.TEMP, 37.0).vital(IittChart.SPO2, 98).vital(IittChart.AVPU, 0).build());
        assertEquals(TriagePriority.GREEN, infant.priority(), "HR 150 is within band for an infant");

        TriageResult tenYearOld = WhoIittEngine.evaluate(allSignsAbsent(10 * 365)
                .vital(IittChart.HR, 150).vital(IittChart.RR, 20)
                .vital(IittChart.TEMP, 37.0).vital(IittChart.SPO2, 98).vital(IittChart.AVPU, 0).build());
        assertEquals(TriagePriority.YELLOW, tenYearOld.priority());
        assertTrue(tenYearOld.requiresClinicianReview(), "HR 150 exceeds the 5-12y band of 140");
    }

    @Test
    @DisplayName("paediatric vital bands are contiguous with no hole to fall through")
    void paediatricBandsAreContiguous() {
        for (int day = 0; day < 12 * 365; day++) {
            PaediatricVitalBand band = PaediatricVitalBand.forAgeDays(day);
            assertTrue(band != null, "no band covers day " + day);
        }
    }

    @Test
    @DisplayName("step 3 asks for a clinician rather than silently promoting to red")
    void highRiskVitalsRequestReviewRatherThanAutoPromoting() {
        TriageResult r = WhoIittEngine.evaluate(completeNormalAdult().vital(IittChart.SPO2, 88).build());

        assertEquals(TriagePriority.YELLOW, r.priority());
        assertTrue(r.requiresClinicianReview(),
                "the chart says up-triage OR immediate review by a supervising clinician — "
                        + "that judgement belongs to a human");
        assertNotEquals(TriagePriority.RED, r.priority(),
                "the engine must not make the up-triage decision the chart gives to a clinician");
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private static TriageFacts.Builder completeNormalAdult() {
        return allSignsAbsent(30 * 365)
                .vital(IittChart.HR, 80).vital(IittChart.RR, 16)
                .vital(IittChart.TEMP, 37.0).vital(IittChart.SPO2, 98).vital(IittChart.AVPU, 0);
    }

    private static TriageFacts.Builder allSignsAbsent(int ageDays) {
        TriageFacts.Builder b = TriageFacts.builder().ageDays(ageDays).pregnant(false);
        for (IittCriterion c : IittChart.adultRed()) c.requiredSigns().forEach(s -> b.sign(s, false));
        for (IittCriterion c : IittChart.adultYellow()) c.requiredSigns().forEach(s -> b.sign(s, false));
        for (IittCriterion c : IittChart.paediatricRed()) c.requiredSigns().forEach(s -> b.sign(s, false));
        for (IittCriterion c : IittChart.paediatricYellow()) c.requiredSigns().forEach(s -> b.sign(s, false));
        return b;
    }
}
