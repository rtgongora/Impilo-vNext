package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import zw.gov.mohcc.impilo.experience.service.GrowthReferenceCurveService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static GrowthController controller(PctServiceClient client) {
        return new GrowthController(new GrowthReferenceCurveService(mapper), client);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstResource(ResponseEntity<Map<String, Object>> response) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        return data.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> derivedOf(Map<String, Object> resource) {
        Map<String, Object> attributes = (Map<String, Object>) resource.get("attributes");
        return (Map<String, Object>) attributes.get("derived");
    }

    @Test
    void listGrowthReturnsResourcesTheClientCanActuallyWalk() {
        // PCT speaks flat snake_case rows and the shell reads attributes.derived. The two
        // were never reconciled, so every growth read landed in the client's error path.
        ResponseEntity<Map<String, Object>> response =
                controller(new StubPctClient()).listGrowth("t1", "req-1", "corr-1", "patient-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));

        Map<String, Object> resource = firstResource(response);
        assertEquals("gm-1", resource.get("id"));
        assertEquals("growth_measurement", resource.get("type"));

        Map<String, Object> derived = derivedOf(resource);
        assertEquals("FENTON_2013", derived.get("standard"));
        assertEquals(30, ((Number) derived.get("postmenstrual_age_weeks")).intValue());
        assertEquals("NEWBORN_BIRTH_RECORD", derived.get("gestational_age_source"));
    }

    @Test
    void aStoredScoreCarriesTheAttributionItsStandardRequires() {
        Map<String, Object> derived = derivedOf(firstResource(
                controller(new StubPctClient()).listGrowth("t1", "req-1", "corr-1", "patient-1")));

        assertEquals("Fenton 2013 Preterm Growth Chart", derived.get("standard_label"));
        @SuppressWarnings("unchecked")
        Map<String, Object> attribution = (Map<String, Object>) derived.get("standard_attribution");
        assertEquals("Fenton 2013 Preterm Growth Chart", attribution.get("required_chart_label"));
        assertEquals("CC BY-NC-ND 4.0", attribution.get("licence"));
        assertEquals("ENGINEERING_SEED", attribution.get("approval_status"));
        assertTrue(((String) attribution.get("citation")).contains("BMC Pediatr. 2013;13:59"));
    }

    @Test
    void anUnscoredMeasurementCarriesTheReasonItWasNotScored() {
        Map<String, Object> derived = derivedOf(firstResource(
                controller(new UnscoredPctClient()).listGrowth("t1", "req-1", "corr-1", "patient-1")));

        assertNull(derived.get("weight_for_age"));
        assertNull(derived.get("standard"));
        assertTrue(((String) derived.get("scoring_note")).contains("not substituted"));
    }

    @Test
    void recordGrowthReturnsTheStoredResourceRatherThanASecondOpinion() {
        GrowthController.RecordGrowthRequest request =
                new GrowthController.RecordGrowthRequest(
                        "patient-1", "enc-1", "doc-1", null,
                        new BigDecimal("10.5"), null, new BigDecimal("75.0"),
                        new BigDecimal("45.0"), null, "STANDING", "Normal");

        ResponseEntity<Map<String, Object>> response =
                controller(new StubPctClient()).recordGrowth("t1", "req-2", "corr-2", request);

        assertEquals(201, response.getStatusCode().value());
        assertEquals("req-2", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> resource = (Map<String, Object>) response.getBody().get("data");
        assertEquals("gm-new", resource.get("id"));
        // The BFF no longer re-scores, so the standard on the way out is the one PCT stamped.
        assertEquals("FENTON_2013", derivedOf(resource).get("standard"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void referenceCurvesComeBackOnThePretermChartForAPretermInfant() {
        ResponseEntity<Map<String, Object>> response = controller(new StubPctClient())
                .referenceCurves("t1", "req-3", "corr-3", "patient-1", "weight_for_age");

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("FENTON_2013", data.get("standard"));
        assertEquals("postmenstrual_weeks", data.get("axis"));

        List<Map<String, Object>> curves = (List<Map<String, Object>>) data.get("curves");
        assertEquals(5, curves.size(), "a growth chart is read against -3, -2, 0, +2 and +3");
        List<Map<String, Object>> median = (List<Map<String, Object>>) curves.get(2).get("points");
        assertFalse(median.isEmpty());
        // Weight is published in grams and must arrive in the unit the screen records.
        double firstValue = ((Number) median.get(0).get("value")).doubleValue();
        assertTrue(firstValue > 0.2d && firstValue < 5.0d,
                "curve values should be kilograms, got " + firstValue);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aChildWithNoUsableContextGetsNoCurvesAndAReason() {
        ResponseEntity<Map<String, Object>> response = controller(new UnknownPatientPctClient())
                .referenceCurves("t1", "req-4", "corr-4", "patient-9", "weight_for_age");

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNull(data.get("standard"));
        assertTrue(((List<?>) data.get("curves")).isEmpty());
        assertNotNull(data.get("unavailable_reason"));
    }

    @Test
    void anUnknownIndicatorIsRejectedRatherThanQuietlyDefaulted() {
        ResponseEntity<Map<String, Object>> response = controller(new StubPctClient())
                .referenceCurves("t1", "req-5", "corr-5", "patient-1", "weight_for_length");

        assertEquals(400, response.getStatusCode().value());
        assertEquals("unknown_indicator", response.getBody().get("error"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    /** A preterm infant: born at 28 weeks, weighed a fortnight later. */
    private static class StubPctClient extends PctServiceClient {
        StubPctClient() { super(new RestTemplate(), endpoints(), mapper); }

        ObjectNode row(String id) {
            ObjectNode row = mapper.createObjectNode();
            row.put("growth_id", id);
            row.put("patient_id", "patient-1");
            row.put("measured_at", "2026-01-15T09:00:00Z");
            row.put("weight_kg", 1.05);
            row.put("age_days", 14);
            row.put("gestational_age_weeks", 28);
            row.put("gestational_age_source", "NEWBORN_BIRTH_RECORD");
            row.put("postmenstrual_age_weeks", 30);
            row.put("weight_for_age_z", -0.41);
            row.put("growth_standard", "FENTON_2013");
            row.put("growth_engine_version", "2.0.0");
            return row;
        }

        @Override public JsonNode listGrowthMeasurements(String patientCpid) {
            ArrayNode rows = mapper.createArrayNode();
            rows.add(row("gm-1"));
            return rows;
        }

        @Override public JsonNode recordGrowthMeasurement(Map<String, Object> body) {
            return row("gm-new");
        }

        @Override public JsonNode getPatientHealthSummary(String cpid) {
            ObjectNode summary = mapper.createObjectNode();
            summary.put("dateOfBirth", LocalDate.now().minusDays(14).toString());
            summary.put("sex", "MALE");
            return summary;
        }
    }

    /** A measurement PCT recorded but could not score. */
    private static final class UnscoredPctClient extends StubPctClient {
        @Override public JsonNode listGrowthMeasurements(String patientCpid) {
            ObjectNode row = row("gm-2");
            row.remove("weight_for_age_z");
            row.remove("growth_standard");
            row.put("scoring_note", "This infant was born at 28 weeks and is now 30 weeks postmenstrual"
                    + " age. The preterm growth reference is not loaded. The measurement is recorded but"
                    + " not scored: WHO term standards are not valid for a preterm infant and are"
                    + " deliberately not substituted.");
            ArrayNode rows = mapper.createArrayNode();
            rows.add(row);
            return rows;
        }
    }

    /** No demographics, no measurements — nothing to choose a standard from. */
    private static final class UnknownPatientPctClient extends StubPctClient {
        @Override public JsonNode listGrowthMeasurements(String patientCpid) {
            return mapper.createArrayNode();
        }

        @Override public JsonNode getPatientHealthSummary(String cpid) {
            return mapper.createObjectNode();
        }
    }
}
