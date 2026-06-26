package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Coverage for {@link FacilityRegulatorBffController} (GAP-2). Stateless composer
 * over the workforce-governance (WGV) facility&lt;-&gt;regulator relationship SoR:
 * happy paths surface the relationships (unwrapping the {@code data} envelope) and
 * upstream failures degrade to 502 BAD_GATEWAY.
 *
 * <p>Follows the existing BFF controller-test convention: plain JUnit, controller
 * invoked directly, downstream client subclassed with overrides.</p>
 */
class FacilityRegulatorBffControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void listReturnsRegulatorRelationshipsOnSuccess() {
        FacilityRegulatorBffController controller =
                new FacilityRegulatorBffController(new SuccessWgvClient());

        ResponseEntity<Map<String, Object>> response = controller.list("FAC-1", "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        // The controller unwraps the upstream {data:[...]} envelope.
        assertNotNull(response.getBody().get("data"));
        assertEquals("corr-1", ((Map<?, ?>) response.getBody().get("meta")).get("correlation_id"));
    }

    @Test
    void listReturnsBadGatewayOnUpstreamFailure() {
        FacilityRegulatorBffController controller =
                new FacilityRegulatorBffController(new FailingWgvClient());

        ResponseEntity<Map<String, Object>> response = controller.list("FAC-1", "req-2", "corr-2");

        assertEquals(502, response.getStatusCode().value());
        assertEquals("REGULATORS_UNAVAILABLE",
                ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void linkReturns201CreatedOnSuccess() {
        FacilityRegulatorBffController controller =
                new FacilityRegulatorBffController(new SuccessWgvClient());

        ResponseEntity<Map<String, Object>> response =
                controller.link("FAC-1", Map.of("councilCode", "MDPCZ"), "req-3", "corr-3");

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    @Test
    void updateStatusReturnsBadGatewayOnUpstreamFailure() {
        FacilityRegulatorBffController controller =
                new FacilityRegulatorBffController(new FailingWgvClient());

        ResponseEntity<Map<String, Object>> response =
                controller.updateStatus("REL-9", Map.of("status", "REVOKED"), "req-4", "corr-4");

        assertEquals(502, response.getStatusCode().value());
        assertEquals("REGULATOR_STATUS_FAILED",
                ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    // ── stub clients ──

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static JsonNode node(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final class SuccessWgvClient extends WorkforceGovernanceClient {
        private SuccessWgvClient() {
            super(new RestTemplate(), endpoints(), MAPPER);
        }

        @Override
        public JsonNode getJson(String relativePath) {
            return node("""
                    {"data":[{"relationshipId":"REL-1","councilCode":"MDPCZ","status":"ACTIVE"}]}
                    """);
        }

        @Override
        public JsonNode postJson(String relativePath, Object body) {
            return node("""
                    {"data":{"relationshipId":"REL-2","status":"PENDING"}}
                    """);
        }

        @Override
        public JsonNode patchJson(String relativePath, Object body) {
            return node("""
                    {"data":{"relationshipId":"REL-2","status":"REVOKED"}}
                    """);
        }
    }

    private static final class FailingWgvClient extends WorkforceGovernanceClient {
        private FailingWgvClient() {
            super(new RestTemplate(), endpoints(), MAPPER);
        }

        @Override
        public JsonNode getJson(String relativePath) {
            throw new RuntimeException("wgv unavailable");
        }

        @Override
        public JsonNode patchJson(String relativePath, Object body) {
            throw new RuntimeException("wgv unavailable");
        }
    }
}
