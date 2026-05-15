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

class AdminReportJobControllerTest {

    @Test
    void listJobsFailClosesWhenReportingUnavailable() {
        AdminReportJobController controller = new AdminReportJobController(new FailingReportingClient());

        var response = controller.listJobs("tenant-1", "req-r-1", "corr-r-1", 0, 20);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("REPORTING_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void listJobsReturnsDataWhenReportingAvailable() {
        AdminReportJobController controller = new AdminReportJobController(new SuccessReportingClient());

        var response = controller.listJobs("tenant-1", "req-r-2", "corr-r-2", 0, 20);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, ((java.util.List<?>) response.getBody().get("data")).size());
    }

    @Test
    void listJobsFailsClosedWhenRunIdMissingFromReportingPayload() {
        AdminReportJobController controller = new AdminReportJobController(new MissingRunIdClient());

        ResponseEntity<Map<String, Object>> response = controller.listJobs("tenant-1", "req-1", "corr-1", 0, 20);

        assertEquals(502, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("REPORTING_UNAVAILABLE", error.get("code"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
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

    private static final class SuccessReportingClient extends ReportingServiceClient {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        private SuccessReportingClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listTenantReportRuns(int page, int size) {
            return MAPPER.createObjectNode()
                    .put("totalElements", 1)
                    .put("totalPages", 1)
                    .set("items", MAPPER.createArrayNode().add(
                            MAPPER.createObjectNode()
                                    .put("runId", "run-1")
                                    .put("reportKey", "weekly")
                                    .put("status", "QUEUED")
                    ));
        }
    }

    private static final class MissingRunIdClient extends ReportingServiceClient {
        private final ObjectMapper objectMapper = new ObjectMapper();

        private MissingRunIdClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listTenantReportRuns(int page, int size) {
            try {
                return objectMapper.readTree("""
                        {
                          "totalElements": 1,
                          "totalPages": 1,
                          "items": [
                            { "status": "QUEUED", "reportKey": "clinical_summary" }
                          ]
                        }
                        """);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
