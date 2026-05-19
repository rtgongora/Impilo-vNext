package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PaymentPlanFinanceBffControllerTest {

    @Test
    void createFailClosesWithTypedErrorWhenCostaUnavailable() {
        PaymentPlanFinanceBffController controller =
                new PaymentPlanFinanceBffController(new FailingCostaClient());

        ResponseEntity<Map<String, Object>> response = controller.create(Map.of(), "req-pp-1", "corr-pp-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COSTA_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class FailingCostaClient extends CostaServiceClient {
        private FailingCostaClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode postFinancePaymentPlan(Map<String, Object> body) {
            throw new RuntimeException("costa unavailable");
        }
    }
}
