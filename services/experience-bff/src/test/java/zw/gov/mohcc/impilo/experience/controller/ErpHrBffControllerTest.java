package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.HrPayrollServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ErpHrBffControllerTest {

    @Test
    void employeesFailClosesWithTypedErrorWhenHrServiceUnavailable() {
        ErpHrBffController controller = new ErpHrBffController(new FailingHrPayrollClient());

        ResponseEntity<?> response = controller.employees("req-hr-1", "corr-hr-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("HR_PAYROLL_UNAVAILABLE", ((Map<?, ?>) body.get("error")).get("code"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class FailingHrPayrollClient extends HrPayrollServiceClient {
        private FailingHrPayrollClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode getEmployees() {
            throw new RuntimeException("hr-payroll unavailable");
        }
    }
}
