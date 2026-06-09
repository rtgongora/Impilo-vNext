package zw.gov.mohcc.impilo.pct.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EdTriageDiscriminatorEngineTest {

    @Test
    void esi_level1_for_airway_compromise() {
        var r = EdTriageDiscriminatorEngine.calculate("ESI", Map.of("airway_compromise", true), Map.of());
        assertEquals(1, r.acuity());
        assertEquals("ESI", r.system());
    }

    @Test
    void esi_level3_for_two_resources() {
        var r = EdTriageDiscriminatorEngine.calculate("ESI", Map.of("resources_needed", 2), Map.of());
        assertEquals(3, r.acuity());
    }

    @Test
    void mts_red_for_resuscitation() {
        var r = EdTriageDiscriminatorEngine.calculate("MTS", Map.of("requires_resuscitation", true), Map.of());
        assertEquals(1, r.acuity());
        assertEquals("RED", r.category());
    }

    @Test
    void mts_orange_for_danger_vitals() {
        var r = EdTriageDiscriminatorEngine.calculate("MTS", Map.of(),
                Map.of("spo2", 88, "heartRate", 120));
        assertEquals(2, r.acuity());
    }

    @Test
    void scoreBoth_recommends_most_urgent() {
        @SuppressWarnings("unchecked")
        Map<String, Object> both = EdTriageDiscriminatorEngine.scoreBoth(
                Map.of("resources_needed", 0), Map.of("spo2", 85));
        assertEquals(2, both.get("recommended_acuity"));
    }
}
