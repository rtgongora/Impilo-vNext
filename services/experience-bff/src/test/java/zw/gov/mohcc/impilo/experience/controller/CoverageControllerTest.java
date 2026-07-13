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

    @Test
    void enrolSubsidy_returns201() {
        CoverageController controller = new CoverageController(new StubCoverageClient());
        ResponseEntity<Map<String, Object>> response =
                controller.enrolSubsidy(Map.of("memberCpid", "cpid-1", "programCode", "SUB-MOHCC-PRIMARY"),
                        "req-5", "corr-5");
        assertEquals(201, response.getStatusCode().value());
        assertEquals("req-5", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void consumeSubsidy_passesThroughCapExceeded400() {
        CoverageController controller = new CoverageController(new CapExceededCoverageClient());
        ResponseEntity<Map<String, Object>> response =
                controller.consumeSubsidy("enr-1", Map.of("amount", "5000.00"), "req-6", "corr-6");
        // The engine's honest 400 (cap exceeded) must reach the UI, not be masked as 502.
        assertEquals(400, response.getStatusCode().value());
        assertEquals("SUBSIDY_CAP_EXCEEDED", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void decidePreauth_passesThroughConflictOnNonPending() {
        CoverageController controller = new CoverageController(new PreauthConflictCoverageClient());
        ResponseEntity<Map<String, Object>> response =
                controller.decidePreauth("pa-1", Map.of("status", "APPROVED"), "req-7", "corr-7");
        assertEquals(409, response.getStatusCode().value());
        assertEquals("PREAUTH_DECISION_REJECTED", ((Map<?, ?>) response.getBody().get("error")).get("code"));
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

        @Override public JsonNode enrolSubsidy(Map<String, Object> body) {
            return mapper.createObjectNode().put("id", "enr-1").put("status", "ACTIVE");
        }

        @Override public JsonNode checkEligibility(Map<String, Object> body) {
            return mapper.createObjectNode().put("eligible", true);
        }
    }

    private static final class CapExceededCoverageClient extends CoverageServiceClient {
        CapExceededCoverageClient() { super(new RestTemplate(), endpoints()); }

        @Override public JsonNode consumeSubsidy(String id, Map<String, Object> body) {
            throw org.springframework.web.client.HttpClientErrorException.create(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Bad Request",
                    org.springframework.http.HttpHeaders.EMPTY,
                    "{\"error\":\"BAD_REQUEST\",\"message\":\"Subsidy annual cap exceeded\"}".getBytes(),
                    null);
        }
    }

    private static final class PreauthConflictCoverageClient extends CoverageServiceClient {
        PreauthConflictCoverageClient() { super(new RestTemplate(), endpoints()); }

        @Override public JsonNode decidePreauth(String id, Map<String, Object> body) {
            throw org.springframework.web.client.HttpClientErrorException.create(
                    org.springframework.http.HttpStatus.CONFLICT, "Conflict",
                    org.springframework.http.HttpHeaders.EMPTY,
                    "{\"error\":\"CONFLICT\",\"message\":\"Preauth is not PENDING\"}".getBytes(),
                    null);
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
