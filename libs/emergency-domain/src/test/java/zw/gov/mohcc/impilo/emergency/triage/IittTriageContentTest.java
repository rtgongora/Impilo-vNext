package zw.gov.mohcc.impilo.emergency.triage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fixed inventory of every criterion transcribed from the published charts.
 *
 * <p><b>Why hard-coded rather than derived.</b> If this test read the inventory from the same code
 * it is testing it would assert nothing — deleting a criterion would delete the assertion with it.
 * The whole point is that <b>removing a criterion from the chart fails the build</b>: a missing
 * criterion is a patient who is not up-triaged, and nothing else in the system would notice.
 *
 * <p>Counts and codes come from the vendored PDFs in
 * {@code docs/reference/who-emergency-care-toolkit/}, verified against the committed text
 * extractions.
 */
class IittTriageContentTest {

    private static Set<String> codes(List<IittCriterion> criteria) {
        return criteria.stream().map(IittCriterion::code).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("adult RED carries every criterion printed on the chart")
    void adultRedInventory() {
        Set<String> actual = codes(IittChart.adultRed());
        List<String> expected = List.of(
                "ADULT_RED_UNRESPONSIVE",
                "ADULT_RED_STRIDOR", "ADULT_RED_RESP_DISTRESS_OR_CYANOSIS",
                "ADULT_RED_CAP_REFILL", "ADULT_RED_WEAK_FAST_PULSE", "ADULT_RED_HEAVY_BLEEDING",
                "ADULT_RED_HR_50_150",
                "ADULT_RED_ACTIVE_CONVULSIONS", "ADULT_RED_ANY_TWO_NEURO", "ADULT_RED_HYPOGLYCAEMIA",
                "ADULT_RED_HIGH_RISK_TRAUMA", "ADULT_RED_POISONING", "ADULT_RED_THREATENED_LIMB",
                "ADULT_RED_SNAKE_BITE", "ADULT_RED_CHEST_ABDO_PAIN_OVER_50",
                "ADULT_RED_ECG_ISCHAEMIA", "ADULT_RED_VIOLENT",
                "ADULT_RED_PREG_HEAVY_BLEEDING", "ADULT_RED_PREG_SEVERE_ABDO_PAIN",
                "ADULT_RED_PREG_SEIZURES", "ADULT_RED_PREG_SEVERE_HEADACHE",
                "ADULT_RED_PREG_VISUAL_CHANGES", "ADULT_RED_PREG_HYPERTENSION",
                "ADULT_RED_PREG_ACTIVE_LABOUR", "ADULT_RED_PREG_TRAUMA");
        for (String code : expected) {
            assertTrue(actual.contains(code), "adult RED criterion missing from the chart: " + code);
        }
        assertEquals(expected.size(), actual.size(),
                "adult RED criterion count changed — a criterion was added or removed");
    }

    @Test
    @DisplayName("paediatric RED carries every criterion printed on the chart")
    void paediatricRedInventory() {
        Set<String> actual = codes(IittChart.paediatricRed());
        List<String> expected = List.of(
                "PAED_RED_UNRESPONSIVE", "PAED_RED_STRIDOR", "PAED_RED_RESP_DISTRESS_OR_CYANOSIS",
                "PAED_RED_CAP_REFILL", "PAED_RED_WEAK_FAST_PULSE", "PAED_RED_HEAVY_BLEEDING",
                "PAED_RED_COLD_EXTREMITIES", "PAED_RED_ANY_TWO_DEHYDRATION",
                "PAED_RED_ACTIVE_CONVULSIONS", "PAED_RED_ALTERED_WITH_MENINGISM",
                "PAED_RED_HYPOGLYCAEMIA",
                "PAED_RED_INFANT_UNDER_8_DAYS", "PAED_RED_UNDER_2M_ABNORMAL_TEMP",
                "PAED_RED_HIGH_RISK_TRAUMA", "PAED_RED_THREATENED_LIMB",
                "PAED_RED_TESTICULAR_PAIN", "PAED_RED_SNAKE_BITE", "PAED_RED_POISONING",
                "PAED_RED_PREGNANT_WITH_ADULT_RED");
        for (String code : expected) {
            assertTrue(actual.contains(code), "paediatric RED criterion missing from the chart: " + code);
        }
        assertEquals(expected.size(), actual.size(),
                "paediatric RED criterion count changed — a criterion was added or removed");
    }

    @Test
    @DisplayName("yellow inventories keep their counts")
    void yellowInventories() {
        assertEquals(23, IittChart.adultYellow().size(),
                "adult YELLOW criterion count changed");
        assertEquals(18, IittChart.paediatricYellow().size(),
                "paediatric YELLOW criterion count changed");
    }

    /**
     * The single most consequential transcription detail on the adult chart. A web search performed
     * during transcription returned the step-3 numbers AS the red criterion; encoding that would
     * have moved the immediate-resuscitation threshold to a materially less abnormal heart rate.
     */
    @Test
    @DisplayName("the two adult heart-rate bands stay distinct and never merge")
    void adultHeartRateBandsAreDistinct() {
        // RED band: <50 or >150.
        TriageFacts redHr = complete().vital(IittChart.HR, 155).build();
        TriageResult red = WhoIittEngine.evaluate(redHr);
        assertEquals(TriagePriority.RED, red.priority());
        assertTrue(red.triggeringCriteria().stream().anyMatch(c -> c.code().equals("ADULT_RED_HR_50_150")));

        // Between the bands: 140 is high-risk (>130) but NOT red (<=150).
        TriageFacts highRiskOnly = complete().vital(IittChart.HR, 140).build();
        TriageResult hr = WhoIittEngine.evaluate(highRiskOnly);
        assertEquals(TriagePriority.YELLOW, hr.priority(),
                "HR 140 is a step-3 high-risk vital sign, not a red criterion");
        assertTrue(hr.requiresClinicianReview());
        assertTrue(hr.highRiskVitalSigns().stream().anyMatch(s -> s.startsWith("HR <60 or >130")));
        assertTrue(hr.triggeringCriteria().isEmpty(), "no red or yellow criterion should have fired");

        // Below both: 100 is neither.
        assertEquals(TriagePriority.GREEN, WhoIittEngine.evaluate(complete().vital(IittChart.HR, 100).build()).priority());
    }

    @Test
    @DisplayName("adult 'any two of' needs two, not one")
    void adultAnyTwoOfNeedsTwo() {
        TriageFacts one = complete().sign(IittChart.HEADACHE, true).build();
        assertFalse(WhoIittEngine.evaluate(one).triggeringCriteria().stream()
                        .anyMatch(c -> c.code().equals("ADULT_RED_ANY_TWO_NEURO")),
                "one of the four must not fire the any-two-of criterion");

        TriageFacts two = complete().sign(IittChart.HEADACHE, true).sign(IittChart.STIFF_NECK, true).build();
        TriageResult r = WhoIittEngine.evaluate(two);
        assertEquals(TriagePriority.RED, r.priority());
        assertTrue(r.triggeringCriteria().stream().anyMatch(c -> c.code().equals("ADULT_RED_ANY_TWO_NEURO")));
    }

    /**
     * The paediatric equivalent is a CONJUNCTION, not "any two of". Altered mental status alone
     * must not fire it, and neither must stiff neck alone — but the two together must.
     */
    @Test
    @DisplayName("paediatric altered-mental-status criterion is a conjunction, not any-two-of")
    void paediatricAlteredMentalStatusIsAConjunction() {
        TriageFacts alteredOnly = completeChild().sign(IittChart.ALTERED_MENTAL_STATUS, true).build();
        assertFalse(WhoIittEngine.evaluate(alteredOnly).triggeringCriteria().stream()
                        .anyMatch(c -> c.code().equals("PAED_RED_ALTERED_WITH_MENINGISM")),
                "altered mental status ALONE must not fire the paediatric criterion");

        TriageFacts withFever = completeChild()
                .sign(IittChart.ALTERED_MENTAL_STATUS, true)
                .sign(IittChart.HYPOTHERMIA_OR_FEVER, true).build();
        assertTrue(WhoIittEngine.evaluate(withFever).triggeringCriteria().stream()
                .anyMatch(c -> c.code().equals("PAED_RED_ALTERED_WITH_MENINGISM")));
    }

    /** The paediatric chart has no numeric HR red criterion at all — only age-banded step-3 vitals. */
    @Test
    @DisplayName("the paediatric chart carries no adult numeric heart-rate red criterion")
    void paediatricChartHasNoAdultHeartRateRedCriterion() {
        assertFalse(codes(IittChart.paediatricRed()).stream().anyMatch(c -> c.contains("HR_50_150")));
    }

    private static TriageFacts.Builder complete() {
        return baseline().ageDays(30 * 365);
    }

    private static TriageFacts.Builder completeChild() {
        return baseline().ageDays(3 * 365);
    }

    /** All step-3 vitals measured and normal, so a test can isolate one variable at a time. */
    private static TriageFacts.Builder baseline() {
        TriageFacts.Builder b = TriageFacts.builder()
                .vital(IittChart.HR, 80).vital(IittChart.RR, 16)
                .vital(IittChart.TEMP, 37.0).vital(IittChart.SPO2, 98).vital(IittChart.AVPU, 0)
                .pregnant(false);
        for (IittCriterion c : IittChart.adultRed()) c.requiredSigns().forEach(s -> b.sign(s, false));
        for (IittCriterion c : IittChart.adultYellow()) c.requiredSigns().forEach(s -> b.sign(s, false));
        for (IittCriterion c : IittChart.paediatricRed()) c.requiredSigns().forEach(s -> b.sign(s, false));
        for (IittCriterion c : IittChart.paediatricYellow()) c.requiredSigns().forEach(s -> b.sign(s, false));
        return b;
    }
}
