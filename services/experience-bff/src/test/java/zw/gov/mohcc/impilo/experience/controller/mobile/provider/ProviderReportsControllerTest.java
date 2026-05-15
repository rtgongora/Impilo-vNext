package zw.gov.mohcc.impilo.experience.controller.mobile.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ReportingServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProviderReportsControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void listReportsReturnsBadGatewayWhenReportingUnavailable() {
        ProviderReportsController controller = new ProviderReportsController(new FailingReportingClient());

        ResponseEntity<Map<String, Object>> response = controller.listReports("tenant-1", "req-1", "corr-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("REPORTING_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void reportDataReturnsRealReportingPayload() {
        ProviderReportsController controller = new ProviderReportsController(new SuccessReportingClient());

        ResponseEntity<Map<String, Object>> response = controller.reportData("facility-kpis", "tenant-1", "req-2", "corr-2");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("data"));
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
            throw new RestClientException("reporting unavailable");
        }

        @Override
        public JsonNode runReport(String reportKey, Map<String, Object> requestOrNull) {
            throw new RestClientException("reporting unavailable");
        }
    }

    private static final class SuccessReportingClient extends ReportingServiceClient {
        private SuccessReportingClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listTenantReportRuns(int page, int size) {
            try {
                return MAPPER.readTree("""
                        [{"reportKey":"facility-kpis","status":"COMPLETED"}]
                        """);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public JsonNode runReport(String reportKey, Map<String, Object> requestOrNull) {
            try {
                return MAPPER.readTree("""
                        {"reportKey":"facility-kpis","entries":[{"key":"Outpatients","value":84}]}
                        """);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
