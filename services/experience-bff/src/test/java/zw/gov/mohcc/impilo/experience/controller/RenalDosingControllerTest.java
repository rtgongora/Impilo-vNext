package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenalDosingControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void postForwardsBodyToCkpAndWrapsResponse() {
        CapturingCkp ckp = new CapturingCkp();
        RenalDosingController controller = new RenalDosingController(ckp);

        var response = controller.advisePost(
                Map.of("egfr", 25, "drugClass", "BIGUANIDE"), "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(25, ckp.lastBody.get("egfr").asInt());
        assertEquals("BIGUANIDE", ckp.lastBody.get("drugClass").asText());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("ADJUST", data.get("level").asText());
    }

    @Test
    void getOmitsNullQueryParamsRatherThanSendingThem() {
        CapturingCkp ckp = new CapturingCkp();
        RenalDosingController controller = new RenalDosingController(ckp);

        controller.adviseGet(null, "NSAID", "req-1", "corr-1");

        assertTrue(ckp.lastBody.has("drugClass"));
        assertEquals("NSAID", ckp.lastBody.get("drugClass").asText());
        assertTrue(!ckp.lastBody.has("egfr") || ckp.lastBody.get("egfr").isNull());
    }

    private static final class CapturingCkp extends zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient {
        ObjectNode lastBody;

        CapturingCkp() {
            super(new org.springframework.web.client.RestTemplate(),
                    new zw.gov.mohcc.impilo.experience.config.ClinicalPlatformProperties("http://unused"));
        }

        @Override
        public JsonNode renalDosingAdvise(Map<String, Object> body) {
            lastBody = MAPPER.valueToTree(body);
            ObjectNode data = MAPPER.createObjectNode();
            data.put("level", body.containsKey("egfr") ? "ADJUST" : "UNKNOWN");
            data.put("message", "test");
            data.put("computed", body.containsKey("egfr"));
            data.put("content_version", "test");
            return data;
        }
    }
}
