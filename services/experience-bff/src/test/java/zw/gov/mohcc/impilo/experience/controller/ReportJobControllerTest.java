package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ReportingServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReportJobControllerTest {

    @Test
    void getJobReturnsNotFoundWhenRunMissing() {
        ReportJobController controller = new ReportJobController(new EmptyReportingClient());

        var response = controller.getJob("run-x", "tenant-1", "req-job-1", "corr-job-1");

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("REPORT_JOB_NOT_FOUND", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void getJobReturnsBadGatewayWhenReportingUnavailable() {
        ReportJobController controller = new ReportJobController(new FailingReportingClient());

        var response = controller.getJob("run-x", "tenant-1", "req-job-2", "corr-job-2");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("REPORTING_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void generateReportFailsClosedWhenReportingReturnsEmptyPayload() {
        ReportJobController controller = new ReportJobController(new EmptyCreateReportClient());
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

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
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
