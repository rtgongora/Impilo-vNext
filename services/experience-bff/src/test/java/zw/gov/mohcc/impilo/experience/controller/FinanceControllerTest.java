package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FinanceControllerTest {

    @Test
    void listBillingReturnsBadGatewayWhenCostaUnavailable() {
        FinanceController controller = new FinanceController(new FailingCostaClient(), new ObjectMapper());

        ResponseEntity<Map<String, Object>> response = controller.listBilling(
                "tenant-1", "req-0", "corr-0", 0, 25, null);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COSTA_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void createPaymentIntentReturnsValidationEnvelopeWhenAmountMissing() {
        FinanceController controller = new FinanceController(new FailingCostaClient(), new ObjectMapper());

        ResponseEntity<Map<String, Object>> response = controller.createPaymentIntent(
                "tenant-1", "req-fin-1", "corr-fin-1", "BILL-1", Map.of("paymentType", "FULL"));

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("MISSING_AMOUNT", ((Map<?, ?>) response.getBody().get("error")).get("code"));
        assertEquals("req-fin-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void createPaymentIntentFailClosesWhenCostaUnavailable() {
        FinanceController controller = new FinanceController(new FailingCostaClient(), new ObjectMapper());

        ResponseEntity<Map<String, Object>> response = controller.createPaymentIntent(
                "tenant-1", "req-fin-2", "corr-fin-2", "BILL-1", Map.of("amount", "10.00"));

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COSTA_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void getBillingDetailFailClosesWhenCostaUnavailable() {
        FinanceController controller = new FinanceController(new FailingCostaClient(), new ObjectMapper());

        ResponseEntity<Map<String, Object>> response = controller.getBillingDetail(
                "tenant-1", "req-fin-3", "corr-fin-3", "BILL-404");

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
        public JsonNode listBills(int page, int size, String status) {
            throw new RuntimeException("costa unavailable");
        }

        @Override
        public JsonNode createPaymentIntent(String billId, String paymentType, String amount) {
            throw new RuntimeException("costa unavailable");
        }

        @Override
        public JsonNode getBill(String billId) {
            throw new RuntimeException("costa unavailable");
        }
    }
}
