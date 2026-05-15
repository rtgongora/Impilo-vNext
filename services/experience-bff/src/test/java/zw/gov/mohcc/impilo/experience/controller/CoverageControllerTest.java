package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.CoverageServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CoverageControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listPlans_returnsDataAndMeta() {
        CoverageController controller = new CoverageController(new StubCoverageClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listPlans(null, "req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void submitClaim_returns201() {
        CoverageController controller = new CoverageController(new StubCoverageClient());
        ResponseEntity<Map<String, Object>> response =
                controller.submitClaim("req-2", "corr-2",
                        Map.of("claimType", "OUTPATIENT", "amount", "100.00"));
        assertEquals(201, response.getStatusCode().value());
        assertEquals("req-2", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void enrollMember_returns201() {
        CoverageController controller = new CoverageController(new StubCoverageClient());
        ResponseEntity<Map<String, Object>> response =
                controller.enrollMember("req-3", "corr-3",
                        Map.of("planId", "plan-1", "memberCpid", "cpid-1"));
        assertEquals(201, response.getStatusCode().value());
        assertEquals("req-3", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void listPlansReturnsBadGatewayWhenCoverageUnavailable() {
        CoverageController controller = new CoverageController(new FailingCoverageClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listPlans(null, "req-4", "corr-4");
        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COVERAGE_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
        assertEquals("req-4", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubCoverageClient extends CoverageServiceClient {
        StubCoverageClient() { super(new RestTemplate(), endpoints()); }

        @Override public JsonNode listPlans() {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "plan-1").put("name", "Basic"));
            return arr;
        }

        @Override public JsonNode submitClaim(Map<String, Object> body) {
            return mapper.createObjectNode().put("id", "claim-1").put("status", "SUBMITTED");
        }

        @Override public JsonNode enrollMember(Map<String, Object> body) {
            return mapper.createObjectNode().put("id", "member-1").put("status", "ACTIVE");
        }

        @Override public JsonNode checkEligibility(Map<String, Object> body) {
            return mapper.createObjectNode().put("eligible", true);
        }
    }

    private static final class FailingCoverageClient extends CoverageServiceClient {
        FailingCoverageClient() { super(new RestTemplate(), endpoints()); }

        @Override
        public JsonNode listPlans() {
            throw new RuntimeException("coverage unavailable");
        }
    }
}
