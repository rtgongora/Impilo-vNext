package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ProcurementServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ErpProcurementBffControllerTest {

    @Test
    void suppliersFailClosesWithTypedErrorWhenProcurementUnavailable() {
        ErpProcurementBffController controller = new ErpProcurementBffController(new FailingProcurementClient());

        ResponseEntity<?> response = controller.suppliers("req-proc-1", "corr-proc-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("PROCUREMENT_UNAVAILABLE", ((Map<?, ?>) body.get("error")).get("code"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class FailingProcurementClient extends ProcurementServiceClient {
        private FailingProcurementClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode getSuppliers() {
            throw new RuntimeException("procurement unavailable");
        }
    }
}
