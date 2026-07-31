package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.config.ClinicalPlatformProperties;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContraceptionMecBffTest {

    private static class StubCkp extends ClinicalKnowledgePlatformClient {
        Map<String, Object> lastBody;
        private static final ObjectMapper MAPPER = new ObjectMapper();

        StubCkp() {
            super(new RestTemplate(), new ClinicalPlatformProperties("http://ckp.test"));
        }

        @Override
        public JsonNode mecAssess(Map<String, Object> body) {
            lastBody = body;
            ObjectNode data = MAPPER.createObjectNode();
            data.put("note", "ok");
            return data;
        }
    }

    @Test
    void assessProxiesToCkp() {
        StubCkp ckp = new StubCkp();
        ContraceptionMecController controller = new ContraceptionMecController(ckp);
        ResponseEntity<Map<String, Object>> response = controller.assess(
                Map.of("facts", Map.of("ageYears", 25)), "req-1", "corr-1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(ckp.lastBody.containsKey("facts"));
    }
}
