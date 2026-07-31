package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.config.ClinicalPlatformProperties;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmergencyBundleBffTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private class StubCkp extends ClinicalKnowledgePlatformClient {
        Map<String, Object> lastPphBody;
        RuntimeException toThrow;

        StubCkp() {
            super(new RestTemplate(), new ClinicalPlatformProperties("http://ckp.test"));
        }

        @Override
        @SuppressWarnings("unchecked")
        public JsonNode emergencyBundleAssessPph(Map<String, Object> body) {
            if (toThrow != null) {
                throw toThrow;
            }
            lastPphBody = (Map<String, Object>) (Map<?, ?>) body;
            ObjectNode data = mapper.createObjectNode();
            data.put("status", "ACTIVE");
            return data;
        }

        @Override
        public JsonNode emergencyBundleAssessEclampsia(Map<String, Object> body) {
            if (toThrow != null) {
                throw toThrow;
            }
            ObjectNode data = mapper.createObjectNode();
            data.put("status", "ACTIVE");
            return data;
        }
    }

    @Test
    @DisplayName("forwards PPH assessment body without coercing null control to false")
    void forwardsPphBodyIntact() {
        StubCkp ckp = new StubCkp();
        var response = new EmergencyBundleController(ckp).assessPph(
                new EmergencyBundleController.AssessRequest(
                        List.of("PPH_MASSAGE"), 10L, 2L, null, false),
                "req-1", "corr-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(ckp.lastPphBody);
        assertFalse(ckp.lastPphBody.containsKey("controlConfirmed"));
    }

    @Test
    @DisplayName("a transport failure is 502 and says it is not a resolved emergency")
    void transportFailureIs502() {
        StubCkp ckp = new StubCkp();
        ckp.toThrow = new IllegalStateException("connection reset");
        var response = new EmergencyBundleController(ckp).assessPph(null, "req-3", "corr-3");

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("pph_bundle_unavailable", response.getBody().get("error"));
        assertTrue(((String) response.getBody().get("message")).contains("not a statement"));
    }
}
