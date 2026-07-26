package zw.gov.mohcc.impilo.experience.clinical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vitals ↔ observations translation. The wire contract has two live dialects (snake_case and
 * camelCase) and two mobile envelopes (flat and {@code attributes}-wrapped); the bridge must feed
 * all of them from the same observation rows, or the remap fixes the route and breaks the shape.
 */
class VitalsObservationBridgeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final VitalsObservationBridge bridge = new VitalsObservationBridge();

    // ── write path ──────────────────────────────────────────────────

    @Test
    void flatSetBecomesOneCodedObservationPerValue_sharingSetProvenance() {
        List<Map<String, Object>> bodies = bridge.toObservationBodies(Map.of(
                "patient_id", "CPID-1",
                "encounter_id", "42",
                "recorded_by", "nurse-1",
                "systolic", 120,
                "diastolic", 80,
                "temperature", new BigDecimal("37.2"),
                "notes", "post-op check"));

        assertEquals(3, bodies.size());
        Map<String, Object> systolic = bodies.stream()
                .filter(b -> "8480-6".equals(b.get("code"))).findFirst().orElseThrow();
        assertEquals("CPID-1", systolic.get("patient_id"));
        assertEquals("42", systolic.get("encounter_id"));
        assertEquals("VITAL_SIGNS", systolic.get("category"));
        assertEquals("mm[Hg]", systolic.get("value_unit"));
        assertEquals(new BigDecimal("120"), systolic.get("value_quantity"));
        assertEquals("VITALS_SET", systolic.get("derived_from_type"));

        // Every observation of the set shares provenance id and effective time — that is what
        // lets the read path reassemble the contact.
        Object setId = systolic.get("derived_from_id");
        Object effectiveAt = systolic.get("effective_at");
        assertNotNull(setId);
        assertTrue(bodies.stream().allMatch(b -> setId.equals(b.get("derived_from_id"))));
        assertTrue(bodies.stream().allMatch(b -> effectiveAt.equals(b.get("effective_at"))));
    }

    @Test
    void camelCaseDialectIsAcceptedOnWrite() {
        List<Map<String, Object>> bodies = bridge.toObservationBodies(Map.of(
                "patientId", "CPID-2",
                "heartRate", 88,
                "oxygenSaturation", new BigDecimal("97.0")));
        assertEquals(2, bodies.size());
        assertTrue(bodies.stream().allMatch(b -> "CPID-2".equals(b.get("patient_id"))));
        assertTrue(bodies.stream().anyMatch(b -> "8867-4".equals(b.get("code"))));
        assertTrue(bodies.stream().anyMatch(b -> "59408-5".equals(b.get("code"))));
    }

    @Test
    void aBodyWithNoVitalValueProducesNoObservations() {
        assertTrue(bridge.toObservationBodies(Map.of("patient_id", "CPID-3")).isEmpty());
    }

    @Test
    void unknownMobileVitalTypeIsRecordedUnderItsOwnCode_notDemotedToANote() {
        Map<String, Object> body = bridge.toSingleObservationBody(
                "CPID-4", "42", "BLOOD_GLUCOSE", new BigDecimal("5.4"), "mmol/L", null);
        assertEquals("blood_glucose", body.get("code"));
        assertEquals("mmol/L", body.get("value_unit"));
        assertEquals(new BigDecimal("5.4"), body.get("value_quantity"));
        assertEquals("VITAL_SIGNS", body.get("category"));
    }

    // ── read path ───────────────────────────────────────────────────

    @Test
    void observationsRegroupIntoSets_bothDialects_withComputedBmi() {
        ArrayNode observations = MAPPER.createArrayNode();
        observations.add(obs("set-1", "systolic", "120", "o-1"));
        observations.add(obs("set-1", "heart_rate", "88", "o-2"));
        observations.add(obs("set-1", "weight", "70", "o-3"));
        observations.add(obs("set-1", "height", "175", "o-4"));

        List<Map<String, Object>> sets = bridge.composeSets(observations);

        assertEquals(1, sets.size());
        Map<String, Object> row = sets.get(0);
        assertEquals("set-1", row.get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) row.get("attributes");
        // Both dialects — the consumers disagree and both must read the same truth.
        assertEquals(new BigDecimal("88"), attributes.get("heart_rate"));
        assertEquals(new BigDecimal("88"), attributes.get("heartRate"));
        assertEquals(new BigDecimal("120"), attributes.get("systolic"));
        assertEquals("CPID-1", attributes.get("patient_id"));
        assertEquals("CPID-1", attributes.get("patientId"));
        // BMI is derived at composition: 70 kg / 1.75m² = 22.9
        assertEquals(new BigDecimal("22.9"), attributes.get("bmi"));
    }

    @Test
    void voidedObservationsAreExcludedFromComposition() {
        ArrayNode observations = MAPPER.createArrayNode();
        ObjectNode voided = obs("set-2", "systolic", "300", "o-9");
        voided.put("status", "ENTERED_IN_ERROR");
        observations.add(voided);
        assertTrue(bridge.composeSets(observations).isEmpty());
    }

    @Test
    void observationsFromOtherWriters_groupByEncounterAndTime() {
        // A form-extracted respiratory rate has no VITALS_SET provenance but is still a vital
        // sign taken at a contact — it must appear on the vitals surface, not vanish.
        ArrayNode observations = MAPPER.createArrayNode();
        ObjectNode formObs = obs(null, "respiratory_rate", "22", "o-5");
        formObs.put("derived_from_type", "FORM_RESPONSE");
        formObs.put("derived_from_id", "resp-1");
        observations.add(formObs);

        List<Map<String, Object>> sets = bridge.composeSets(observations);
        assertEquals(1, sets.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) sets.get(0).get("attributes");
        assertEquals(new BigDecimal("22"), attributes.get("respiratoryRate"));
    }

    @Test
    void mobileRowCarriesBothEnvelopes() {
        JsonNode observation = obs("set-3", "heart_rate", "90", "o-7");
        Map<String, Object> row = bridge.toMobileRow(observation);

        // Flat keys (VitalsMonitorScreen) and the attributes mirror (vitalsService) must agree.
        assertEquals("HEART_RATE", row.get("vital_type"));
        assertEquals(new BigDecimal("90"), row.get("value"));
        assertEquals("o-7", row.get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) row.get("attributes");
        assertEquals("HEART_RATE", attributes.get("vital_type"));
        assertEquals(new BigDecimal("90"), attributes.get("value"));
        assertNotNull(attributes.get("measured_at"));
    }

    @Test
    void nonVitalObservationsAreNotVitals() {
        ObjectNode lab = obs(null, "hba1c", "6.5", "o-8");
        lab.put("category", "LABORATORY");
        assertFalse(bridge.isLiveVitalSign(lab));
    }

    // ── fixture ─────────────────────────────────────────────────────

    private static ObjectNode obs(String setId, String code, String value, String observationId) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("observation_id", observationId);
        node.put("patient_id", "CPID-1");
        node.put("subject_cpid", "CPID-1");
        node.put("encounter_id", "42");
        node.put("code", code);
        node.put("category", "VITAL_SIGNS");
        node.put("value_quantity", new BigDecimal(value));
        node.put("value_unit", "u");
        node.put("effective_at", "2026-07-26T10:15:30+02:00");
        node.put("recorded_at", "2026-07-26T10:15:31+02:00");
        node.put("recorded_by", "nurse-1");
        node.put("status", "FINAL");
        if (setId != null) {
            node.put("derived_from_type", "VITALS_SET");
            node.put("derived_from_id", setId);
        }
        return node;
    }
}
