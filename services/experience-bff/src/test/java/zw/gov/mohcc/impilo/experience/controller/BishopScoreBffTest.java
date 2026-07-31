package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.config.ClinicalPlatformProperties;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BishopScoreBffTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private class StubCkp extends ClinicalKnowledgePlatformClient {
        Map<String, Object> lastBody;
        RuntimeException toThrow;

        StubCkp() {
            super(new RestTemplate(), new ClinicalPlatformProperties("http://ckp.test"));
        }

        @Override
        @SuppressWarnings("unchecked")
        public JsonNode bishopAssess(Map<String, Object> body) {
            if (toThrow != null) {
                throw toThrow;
            }
            lastBody = (Map<String, Object>) (Map<?, ?>) body;
            ObjectNode data = mapper.createObjectNode();
            data.put("interpretation", "INCOMPLETE");
            data.putNull("score");
            return data;
        }
    }

    @Test
    @DisplayName("forwards assess body without coercing blank components to zero")
    void forwardsBodyIntact() {
        StubCkp ckp = new StubCkp();
        var response = new BishopScoreController(ckp).assess(
                new BishopScoreController.AssessRequest("DIL_3_4", null, "+2", "FIRM", "POSTERIOR"),
                "req-1", "corr-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(ckp.lastBody);
        assertFalse(ckp.lastBody.containsKey("effacement"));
        assertEquals("DIL_3_4", ckp.lastBody.get("dilation"));
    }

    @Test
    @DisplayName("classify-form maps bishop.* answers and omits blanks")
    void classifyFormOmitsBlanks() {
        StubCkp ckp = new StubCkp();
        var response = new BishopScoreController(ckp).classifyFromForm(
                new BishopScoreController.FormAssessRequest(
                        "impilo.maternal.bishop.v1",
                        Map.of("bishop.dilation", "CLOSED", "bishop.consistency", "SOFT")),
                "req-2", "corr-2");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(ckp.lastBody);
        assertEquals("CLOSED", ckp.lastBody.get("dilation"));
        assertEquals("SOFT", ckp.lastBody.get("consistency"));
        assertFalse(ckp.lastBody.containsKey("effacement"));
    }

    @Test
    @DisplayName("a transport failure is 502 and says it is not a favourable cervix")
    void transportFailureIs502() {
        StubCkp ckp = new StubCkp();
        ckp.toThrow = new IllegalStateException("connection reset");
        var response = new BishopScoreController(ckp).assess(null, "req-3", "corr-3");

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("bishop_score_unavailable", response.getBody().get("error"));
        assertTrue(((String) response.getBody().get("message")).contains("not a statement"));
    }
}
