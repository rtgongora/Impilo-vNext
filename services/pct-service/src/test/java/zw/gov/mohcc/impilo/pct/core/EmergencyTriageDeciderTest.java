package zw.gov.mohcc.impilo.pct.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The safety heart of the triage demotion (R7). WHO IITT is the acuity authority; ESI/MTS are
 * advisory and can never set the acuity or overwrite the tool of record; an unassessed patient is
 * refused, never written down as acuity 3. Every branch is exercised here without a running
 * service, because {@link EmergencyTriageDecider} is a pure function of the request.
 */
class EmergencyTriageDeciderTest {

    /** An adult, complete enough for the engine to reach a definite decision on its own. */
    private static Map<String, Object> adult() {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("age", 40);
        return b;
    }

    private static Map<String, Object> vitals(Map<String, Object> body, Object hr, Object rr,
                                              Object temp, Object spo2, Object avpu) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("hr", hr);
        v.put("rr", rr);
        v.put("temp", temp);
        v.put("spo2", spo2);
        v.put("avpu", avpu);
        body.put("vitals", v);
        return body;
    }

    private static Map<String, Object> signs(Map<String, Object> body, Object... kv) {
        Map<String, Object> s = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            s.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        body.put("emergencySigns", s);
        return body;
    }

    @Test
    @DisplayName("A red criterion is acuity 1, tool WHO_IITT, priority RED")
    void redCriterion() {
        Map<String, Object> body = signs(adult(), "unresponsive", true);
        TriageDecision d = EmergencyTriageDecider.decide(body, null, "WHO_IITT", null);
        assertThat(d.triageable()).isTrue();
        assertThat(d.acuity()).isEqualTo(1);
        assertThat(d.triageTool()).isEqualTo("WHO_IITT");
        assertThat(d.iittPriority()).isEqualTo("RED");
        assertThat(d.triggeringFindings()).anyMatch(s -> s.toLowerCase().contains("unresponsive"));
    }

    @Test
    @DisplayName("A yellow criterion is acuity 3, no review")
    void yellowCriterion() {
        Map<String, Object> body = signs(adult(), "wheezing", true);
        TriageDecision d = EmergencyTriageDecider.decide(body, null, "WHO_IITT", null);
        assertThat(d.acuity()).isEqualTo(3);
        assertThat(d.iittPriority()).isEqualTo("YELLOW");
        assertThat(d.requiresClinicianReview()).isFalse();
    }

    @Test
    @DisplayName("Step-3 high-risk vitals hold at YELLOW but flag clinician review — the chart's own instruction")
    void highRiskVitalsRequireReview() {
        // All five vitals measured; HR 140 is in the adult high-risk band (<60/>130) but not the RED band (<50/>150).
        Map<String, Object> body = vitals(adult(), 140, 16, 37.0, 98, "A");
        TriageDecision d = EmergencyTriageDecider.decide(body, null, "WHO_IITT", null);
        assertThat(d.acuity()).isEqualTo(3);
        assertThat(d.iittPriority()).isEqualTo("YELLOW");
        assertThat(d.requiresClinicianReview()).isTrue();
        assertThat(d.reviewReason()).contains("high-risk");
    }

    @Test
    @DisplayName("An unmeasured patient is NOT_TRIAGEABLE — never GREEN, never acuity 3")
    void thinAssessmentIsRefused() {
        // Only HR measured (normal), nothing else — the engine cannot claim GREEN.
        Map<String, Object> body = adult();
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("hr", 80);
        body.put("vitals", v);
        TriageDecision d = EmergencyTriageDecider.decide(body, null, "WHO_IITT", null);
        assertThat(d.triageable()).isFalse();
        assertThat(d.acuity()).isNull();
        assertThat(d.refusalCode()).isEqualTo("not_triageable");
        assertThat(d.unassessedInputs()).isNotEmpty();
    }

    @Test
    @DisplayName("Age is required — a missing age is refused, never routed to a default chart")
    void missingAgeIsRefused() {
        Map<String, Object> body = new LinkedHashMap<>();
        signs(body, "unresponsive", true); // has IITT inputs but no age
        TriageDecision d = EmergencyTriageDecider.decide(body, null, "WHO_IITT", null);
        assertThat(d.triageable()).isFalse();
        assertThat(d.refusalCode()).isEqualTo("age_required");
    }

    @Test
    @DisplayName("No IITT inputs and no explicit acuity is refused — ESI/MTS cannot fill the gap (replaces acuity=3)")
    void noInputsNoAcuityIsRefused() {
        Map<String, Object> advisory = Map.of("recommended_acuity", 2, "recommended_system", "ESI");
        TriageDecision d = EmergencyTriageDecider.decide(new LinkedHashMap<>(), advisory, "ESI", null);
        assertThat(d.triageable()).isFalse();
        assertThat(d.refusalCode()).isEqualTo("not_triageable");
        // Even with an advisory ESI 2 present, the advisory does not become the acuity.
        assertThat(d.acuity()).isNull();
    }

    @Test
    @DisplayName("A manually-entered acuity is honoured; the tool is the declared system, never the advisory")
    void manualAcuityHonoured() {
        Map<String, Object> advisory = Map.of("recommended_acuity", 1, "recommended_system", "MTS");
        TriageDecision d = EmergencyTriageDecider.decide(new LinkedHashMap<>(), advisory, "ESI", 2);
        assertThat(d.triageable()).isTrue();
        assertThat(d.acuity()).isEqualTo(2);       // the human's number, not the advisory's 1
        assertThat(d.triageTool()).isEqualTo("ESI"); // the declared system, not "MTS" from Math.min
        assertThat(d.iittPriority()).isNull();
    }

    @Test
    @DisplayName("Advisory ESI/MTS never sets acuity; a material discrepancy flags review instead of up-triaging")
    void advisoryDiscrepancyFlagsReviewNeverSetsAcuity() {
        // IITT says YELLOW (acuity 3); advisory says 1 (two steps more urgent) → review, acuity stays 3.
        Map<String, Object> body = signs(adult(), "wheezing", true);
        Map<String, Object> advisory = Map.of("recommended_acuity", 1, "recommended_system", "ESI");
        TriageDecision d = EmergencyTriageDecider.decide(body, advisory, "WHO_IITT", null);
        assertThat(d.acuity()).isEqualTo(3);          // IITT authority, not the advisory 1
        assertThat(d.requiresClinicianReview()).isTrue();
        assertThat(d.reviewReason()).contains("more urgent");
        assertThat(d.advisoryScores()).isSameAs(advisory);
    }

    @Test
    @DisplayName("A one-step advisory difference is not a discrepancy — no review noise")
    void oneStepDifferenceIsNotFlagged() {
        Map<String, Object> body = signs(adult(), "wheezing", true); // YELLOW acuity 3
        Map<String, Object> advisory = Map.of("recommended_acuity", 2, "recommended_system", "ESI");
        TriageDecision d = EmergencyTriageDecider.decide(body, advisory, "WHO_IITT", null);
        assertThat(d.acuity()).isEqualTo(3);
        assertThat(d.requiresClinicianReview()).isFalse();
    }

    @Test
    @DisplayName("Explicit acuity alongside a runnable IITT is an override: IITT stays the tier of record and review is flagged")
    void explicitAcuityOverIittIsFlaggedButIittWins() {
        Map<String, Object> body = signs(adult(), "unresponsive", true); // IITT RED, acuity 1
        TriageDecision d = EmergencyTriageDecider.decide(body, null, "WHO_IITT", 4);
        assertThat(d.acuity()).isEqualTo(1);            // IITT authority retained, not the entered 4
        assertThat(d.iittPriority()).isEqualTo("RED");
        assertThat(d.triageTool()).isEqualTo("WHO_IITT");
        assertThat(d.requiresClinicianReview()).isTrue();
        assertThat(d.reviewReason()).contains("override");
    }

    @Test
    @DisplayName("Partial vitals with no age but an explicit acuity falls back to the manual entry — legacy callers keep working")
    void partialVitalsNoAgeExplicitAcuityFallsBackToManual() {
        // The legacy shape: some vitals, no age, an explicit acuity. IITT cannot run (no age);
        // the clinician's number stands rather than a 422.
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("hr", 110);
        v.put("spo2", 92);
        body.put("vitals", v);
        TriageDecision d = EmergencyTriageDecider.decide(body, null, "IMPILO_5", 2);
        assertThat(d.triageable()).isTrue();
        assertThat(d.acuity()).isEqualTo(2);
        assertThat(d.triageTool()).isEqualTo("IMPILO_5");
        assertThat(d.iittPriority()).isNull();
    }

    @Test
    @DisplayName("A manual acuity over an incomplete IITT assessment is allowed but flagged, and the unassessed inputs are recorded")
    void manualOverIncompleteIittIsFlagged() {
        Map<String, Object> body = adult();
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("hr", 80); // only one vital -> IITT NOT_TRIAGEABLE
        body.put("vitals", v);
        TriageDecision d = EmergencyTriageDecider.decide(body, null, "IMPILO_5", 3);
        assertThat(d.triageable()).isTrue();
        assertThat(d.acuity()).isEqualTo(3);
        assertThat(d.requiresClinicianReview()).isTrue();
        assertThat(d.unassessedInputs()).isNotEmpty();
    }

    @Test
    @DisplayName("AVPU 'A' is not an abnormal finding; an unmeasured vital never scores as normal")
    void avpuAlertIsNormalMeasuredButAbsentIsNotNormal() {
        // Fully measured, all normal incl. AVPU=A, no signs asserted -> the engine still refuses because
        // the criterion signs were never recorded. Proves absence is not treated as a normal finding.
        Map<String, Object> body = vitals(adult(), 80, 16, 37.0, 98, "A");
        TriageDecision d = EmergencyTriageDecider.decide(body, null, "WHO_IITT", null);
        // No red, no yellow, no high-risk vitals: reaches the unassessed gate, which withholds GREEN.
        assertThat(d.triageable()).isFalse();
        assertThat(d.refusalCode()).isEqualTo("not_triageable");
    }
}
