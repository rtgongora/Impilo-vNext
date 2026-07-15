package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ReportingServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReportJobControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void getJobReturnsNotFoundWhenRunMissing() {
        ReportJobController controller = new ReportJobController(new EmptyReportingClient(), MAPPER);

        var response = controller.getJob("run-x", "tenant-1", "req-job-1", "corr-job-1");

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("REPORT_JOB_NOT_FOUND", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void getJobReturnsBadGatewayWhenReportingUnavailable() {
        ReportJobController controller = new ReportJobController(new FailingReportingClient(), MAPPER);

        var response = controller.getJob("run-x", "tenant-1", "req-job-2", "corr-job-2");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("REPORTING_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void generateThenGetJob_roundTripWhenReportingSucceeds() {
        ReportJobController controller = new ReportJobController(new RoundTripReportingClient(), MAPPER);
        ReportJobController.GenerateReportRequest request = new ReportJobController.GenerateReportRequest(
                "clinical_summary",
                Map.of("facility_id", "f-1"),
                "analyst-1");

        ResponseEntity<Map<String, Object>> created = controller.generateReport(
                "tenant-1", "pod-1", "req-gen", "corr-gen", null, request);
        assertEquals(201, created.getStatusCode().value());

        ResponseEntity<Map<String, Object>> fetched = controller.getJob(
                "run-42", "tenant-1", "req-get", "corr-get");
        assertEquals(200, fetched.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) fetched.getBody().get("data");
        assertEquals("run-42", data.get("id"));
    }

    @Test
    void generateReportFailsClosedWhenReportingReturnsEmptyPayload() {
        ReportJobController controller = new ReportJobController(new EmptyCreateReportClient(), MAPPER);
        ReportJobController.GenerateReportRequest request = new ReportJobController.GenerateReportRequest(
                "clinical_summary",
                Map.of("facility_id", "f-1"),
                "tester");

        ResponseEntity<Map<String, Object>> response = controller.generateReport(
                "tenant-1", "pod-1", "req-1", "corr-1", null, request);

        assertEquals(502, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("REPORTING_UNAVAILABLE", error.get("code"));
    }

    @Test
    void runReport_delegatesAndWraps201_whenVisibilityAllows() {
        ReportJobController controller = new ReportJobController(new RunReportingClient(), MAPPER);

        ResponseEntity<Map<String, Object>> response = controller.runReport(
                "theatre-utilisation", "req-run", "corr-run", Map.of(), new MockHttpServletRequest());

        assertEquals(201, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("run-99", data.get("runId").asText());
    }

    @Test
    void runReport_failsFastWith403_whenExportVisibilityDenies() {
        ReportJobController controller = new ReportJobController(new RunReportingClient(), MAPPER);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TrustHeaders.EXPORT_POLICY, "AGGREGATE_ONLY");

        ResponseEntity<Map<String, Object>> response = controller.runReport(
                "theatre-utilisation", "req-deny", "corr-deny", Map.of(), request);

        assertEquals(403, response.getStatusCode().value());
        assertEquals("EXPORT_VISIBILITY_DENIED", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class RunReportingClient extends ReportingServiceClient {
        private static final ObjectMapper M = new ObjectMapper();

        private RunReportingClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode runReport(String reportKey, Map<String, Object> requestOrNull) {
            return M.createObjectNode().put("runId", "run-99").put("reportKey", reportKey).put("status", "QUEUED");
        }
    }

    private static final class EmptyReportingClient extends ReportingServiceClient {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        private EmptyReportingClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listTenantReportRuns(int page, int size) {
            return MAPPER.createObjectNode().set("items", MAPPER.createArrayNode());
        }
    }

    private static final class FailingReportingClient extends ReportingServiceClient {
        private FailingReportingClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listTenantReportRuns(int page, int size) {
            throw new RuntimeException("reporting unavailable");
        }
    }

    private static final class RoundTripReportingClient extends ReportingServiceClient {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        private RoundTripReportingClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode createReport(Map<String, Object> request) {
            return MAPPER.createObjectNode()
                    .put("runId", "run-42")
                    .put("reportKey", "clinical_summary")
                    .put("status", "QUEUED")
                    .put("createdBy", "analyst-1")
                    .put("createdAt", "2026-06-08T00:00:00Z");
        }

        @Override
        public JsonNode listTenantReportRuns(int page, int size) {
            ArrayNode items = MAPPER.createArrayNode();
            items.add(MAPPER.createObjectNode()
                    .put("runId", "run-42")
                    .put("reportKey", "clinical_summary")
                    .put("status", "COMPLETED")
                    .put("createdBy", "analyst-1")
                    .put("createdAt", "2026-06-08T00:00:00Z"));
            return MAPPER.createObjectNode().set("items", items);
        }
    }

    private static final class EmptyCreateReportClient extends ReportingServiceClient {
        private EmptyCreateReportClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode createReport(Map<String, Object> request) {
            return null;
        }
    }
}
