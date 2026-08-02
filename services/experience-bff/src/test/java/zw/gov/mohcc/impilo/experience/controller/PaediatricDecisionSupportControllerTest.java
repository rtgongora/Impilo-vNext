package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.config.ClinicalPlatformProperties;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two properties are under test, and they pull in opposite directions.
 *
 * <p>An unreachable engine must never look like a well child: every empty answer these four
 * endpoints can give is the most reassuring sentence the surface could show, so a failure is a 502
 * carrying no {@code data} key at all.</p>
 *
 * <p>An engine's own refusal must never look like an outage: {@code INDETERMINATE},
 * {@code incomplete}, {@code interpretation_blocked} and a 400 for a missing date of birth are the
 * engines working. Flattening those into 502 would throw away the reason and tell a clinician to
 * go and check the network over a field they could have filled in.</p>
 */
class PaediatricDecisionSupportControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    // ---- an unreachable engine is never a reassuring answer ----

    @Test
    void anUnreachableImnciEngineIs502AndCarriesNoDataKey() {
        var controller = new PaediatricDecisionSupportController(new UnreachableCkpClient());

        ResponseEntity<Map<String, Object>> response =
                controller.imnciClassify("req-1", "corr-1", Map.of("ageDays", 420));

        assertEquals(502, response.getStatusCode().value());
        assertEquals("imnci_classification_unavailable", response.getBody().get("error"));
        // No data key at all: a caller that reads `data` first must not find an empty object and
        // end up rendering the green row.
        assertFalse(response.getBody().containsKey("data"));
        assertTrue(String.valueOf(response.getBody().get("message"))
                .contains("not a classification of no illness"));
    }

    @Test
    void anUnreachableDangerSignEngineSaysNoSignWasChecked() {
        var controller = new PaediatricDecisionSupportController(new UnreachableCkpClient());

        ResponseEntity<Map<String, Object>> response =
                controller.dangerSigns("req-2", "corr-2", Map.of("ageDays", 10));

        assertEquals(502, response.getStatusCode().value());
        assertFalse(response.getBody().containsKey("data"));
        assertTrue(String.valueOf(response.getBody().get("message"))
                .contains("not the same as no sign being present"));
    }

    @Test
    void anUnreachableForecastIsNotAnUpToDateChild() {
        var controller = new PaediatricDecisionSupportController(new UnreachableCkpClient());

        ResponseEntity<Map<String, Object>> response =
                controller.immunisationForecast("req-3", "corr-3", Map.of("date_of_birth", "2025-01-01"));

        assertEquals(502, response.getStatusCode().value());
        assertFalse(response.getBody().containsKey("data"));
        assertTrue(String.valueOf(response.getBody().get("message")).contains("being up to date"));
    }

    @Test
    void anUnreachableGrowthEngineIsNotANormallyGrowingChild() {
        var controller = new PaediatricDecisionSupportController(new UnreachableCkpClient());

        ResponseEntity<Map<String, Object>> response =
                controller.growthInterpret("req-4", "corr-4", Map.of("measurements", java.util.List.of()));

        assertEquals(502, response.getStatusCode().value());
        assertFalse(response.getBody().containsKey("data"));
        assertTrue(String.valueOf(response.getBody().get("message")).contains("growing normally"));
    }

    @Test
    void a5xxFromTheEngineIsAnOutageNotAPassThrough() {
        var controller = new PaediatricDecisionSupportController(new ServerErrorCkpClient());

        ResponseEntity<Map<String, Object>> response =
                controller.imnciClassify("req-5", "corr-5", Map.of("ageDays", 420));

        assertEquals(502, response.getStatusCode().value());
        assertFalse(response.getBody().containsKey("data"));
    }

    // ---- an engine's refusal is a success and survives the hop ----

    @Test
    void anIndeterminateClassificationIsA200ThatKeepsItsMissingInputs() {
        // The whole point of the engine. If this arrives as an error or an empty result, the
        // surface has no way to tell the clinician which observation would resolve it.
        var controller = new PaediatricDecisionSupportController(new StubCkpClient());

        ResponseEntity<Map<String, Object>> response =
                controller.imnciClassify("req-6", "corr-6", Map.of("ageDays", 420));

        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertNotNull(data);
        assertEquals("INDETERMINATE", data.get("classifications").get(0).get("status").asText());
        assertEquals("chestIndrawing",
                data.get("classifications").get(0).get("missing_inputs").get(0).asText());
        assertTrue(data.get("incomplete").asBoolean());
        assertEquals("req-6", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void aForecastAskedForWithoutADateOfBirthKeepsIts400AndItsReason() {
        // Something the caller can fix. Reporting it as 502 would send them to check the network.
        var controller = new PaediatricDecisionSupportController(new BadRequestCkpClient());

        ResponseEntity<Map<String, Object>> response =
                controller.immunisationForecast("req-7", "corr-7", Map.of());

        assertEquals(400, response.getStatusCode().value());
        assertEquals(400, response.getBody().get("status"));
        assertEquals("paediatric_request_refused", response.getBody().get("error"));
        assertTrue(String.valueOf(response.getBody().get("message")).contains("date_of_birth_required"));
    }

    @Test
    void aBlockedGrowthInterpretationIsA200ThatKeepsTheBlockedFlag() {
        var controller = new PaediatricDecisionSupportController(new StubCkpClient());

        ResponseEntity<Map<String, Object>> response =
                controller.growthInterpret("req-8", "corr-8", Map.of("measurements", java.util.List.of()));

        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertTrue(data.get("interpretation_blocked").asBoolean());
        // The engine suppresses the signals itself; the proxy must not put anything back.
        assertEquals(0, data.get("signals").size());
    }

    @Test
    void aBlockedByIntervalDoseIsA200ThatKeepsItsGateDateAndIsNotActionable() {
        var controller = new PaediatricDecisionSupportController(new StubCkpClient());

        ResponseEntity<Map<String, Object>> response =
                controller.immunisationForecast("req-9", "corr-9", Map.of("date_of_birth", "2025-01-01"));

        assertEquals(200, response.getStatusCode().value());
        JsonNode dose = ((JsonNode) response.getBody().get("data")).get("doses").get(0);
        assertEquals("BLOCKED_BY_INTERVAL", dose.get("status").asText());
        assertFalse(dose.get("actionable_today").asBoolean());
        assertEquals("2026-08-20", dose.get("earliest_date").asText());
    }

    // ---- stubs ----

    private static ClinicalPlatformProperties properties() {
        return new ClinicalPlatformProperties("http://localhost:8270");
    }

    private static class StubCkpClient extends ClinicalKnowledgePlatformClient {
        StubCkpClient() {
            super(new RestTemplate(), properties());
        }

        @Override public JsonNode paediatricImnciClassify(Map<String, Object> body) {
            ObjectNode row = mapper.createObjectNode();
            row.put("table_id", "cough");
            row.put("status", "INDETERMINATE");
            row.putNull("classification");
            row.putNull("colour");
            row.putArray("missing_inputs").add("chestIndrawing");
            ObjectNode data = mapper.createObjectNode();
            data.set("classifications", mapper.createArrayNode().add(row));
            data.put("incomplete", true);
            data.putArray("outstanding_assessments").add("chestIndrawing");
            return data;
        }

        @Override public JsonNode paediatricGrowthInterpret(Map<String, Object> body) {
            ObjectNode data = mapper.createObjectNode();
            data.put("assessable", true);
            data.put("interpretation_blocked", true);
            data.putArray("signals");
            return data;
        }

        @Override public JsonNode paediatricImmunisationForecast(Map<String, Object> body) {
            ObjectNode dose = mapper.createObjectNode();
            dose.put("series", "PENTA");
            dose.put("dose_number", 2);
            dose.put("status", "BLOCKED_BY_INTERVAL");
            dose.put("actionable_today", false);
            dose.put("earliest_date", "2026-08-20");
            ObjectNode data = mapper.createObjectNode();
            data.set("doses", mapper.createArrayNode().add(dose));
            return data;
        }

        @Override public JsonNode paediatricDangerSigns(Map<String, Object> body) {
            ObjectNode data = mapper.createObjectNode();
            data.put("incomplete", true);
            return data;
        }
    }

    private static final class UnreachableCkpClient extends StubCkpClient {
        @Override public JsonNode paediatricImnciClassify(Map<String, Object> body) {
            throw new IllegalStateException("connection refused");
        }

        @Override public JsonNode paediatricGrowthInterpret(Map<String, Object> body) {
            throw new IllegalStateException("connection refused");
        }

        @Override public JsonNode paediatricImmunisationForecast(Map<String, Object> body) {
            throw new IllegalStateException("connection refused");
        }

        @Override public JsonNode paediatricDangerSigns(Map<String, Object> body) {
            throw new IllegalStateException("connection refused");
        }
    }

    private static final class ServerErrorCkpClient extends StubCkpClient {
        @Override public JsonNode paediatricImnciClassify(Map<String, Object> body) {
            throw HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal Server Error", null, "boom".getBytes(), null);
        }
    }

    private static final class BadRequestCkpClient extends StubCkpClient {
        @Override public JsonNode paediatricImmunisationForecast(Map<String, Object> body) {
            throw HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null,
                    ("{\"error\":\"date_of_birth_required\",\"message\":\"An immunisation forecast is "
                            + "entirely a function of age and interval.\"}").getBytes(), null);
        }
    }
}
